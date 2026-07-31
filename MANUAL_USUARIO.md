# Manual de Usuario y Referencia Técnica — MyTube

**Versión del documento:** 1.0 (31/07/2026)
**Aplicación:** MyTube (paquete `com.miappvideos`)
**Carpeta del proyecto:** `C:\xampp\htdocs\my tube`
**Repositorio:** https://github.com/JoseAdolfo19/my-tube

---

## 1. ¿Qué es MyTube?

MyTube es un cliente de YouTube **sin anuncios**, orientado a música, hecho en Kotlin con Android nativo. Usa la YouTube Data API v3 (catálogo: tendencias, búsquedas, suscripciones) y obtiene los streams de audio directamente del protocolo InnerTube de YouTube (perfil IOS), con respaldo a NewPipeExtractor. La reproducción es **solo audio** (modo música): cada video del feed se reproduce como canción.

---

## 2. Lo que SÍ se puede hacer (estado actual)

### Pantalla principal (feed)
- Ver un feed estilo YouTube: barra superior con menú (☰), logo, botón crear, búsqueda y avatar de cuenta.
- Categorías por chips: **Todo, Música, Mixes, Videojuegos, Noticias, Deportes, Comedia, Educación** (cada una lanza una búsqueda temática).
- Tarjetas de video 16:9 con: miniatura, duración superpuesta, título, canal, vistas abreviadas (K/M/B) y fecha relativa ("hoy", "ayer", "hace X días/meses/años").
- Buscar videos (lupa del toolbar o tecla "buscar" del teclado).
- Jalar hacia abajo para refrescar el feed (SwipeRefresh).
- Navegación inferior: Inicio, Tendencias, Música, Suscripciones y Cuenta.

### Reproducción (modo música)
- Reproducir **audio** de cualquier video tocando su tarjeta (sin video, es una app de música).
- Pantalla de reproducción: reproductor arriba (con controles de ExoPlayer: play/pausa, anterior, siguiente, avance/retroceso, tiempo) y **cola de reproducción** debajo con la canción actual resaltada en rojo.
- Tocar un elemento de la cola cambia de canción sin salir de la pantalla.
- Botón ⋮ en cada video: **Reproducir** o **Agregar a la cola** (evita duplicados).
- Botón ⋮ en la cola: **Reproducir ahora** o **Quitar de la cola**.
- **Shuffle (aleatorio)**: botón de la barra de controles; al activarse, `Siguiente` salta a una canción aleatoria distinta de la actual.
- **Repeat (repetir)**: 3 estados al pulsar — Apagado / Repetir toda la lista / Repetir la canción actual (al terminar vuelve a empezar).
- Al terminar la lista, la reproducción se detiene (a menos que esté activo repetir-lista).

### Mini reproductor y gestos
- Al salir de la pantalla de reproducción queda un **mini-reproductor** (miniatura, título, canal, anterior, play/pausa, siguiente, expandir).
- Gestos sobre el mini-reproductor:
  - **Swipe a la izquierda/derecha**: siguiente / anterior canción.
  - **Tap**: expandir a pantalla completa.
- Gestos en el reproductor expandido:
  - **Swipe hacia abajo**: colapsar a mini-reproductor.

### Reproducción en segundo plano
- Botón de **auriculares (reproducción en segundo plano)**: lanza `PlayerService` con notificación persistente ("Reproduciendo música en segundo plano"); al cerrar la app la música sigue (el servicio usa `START_STICKY` y `startForeground`).
- **Picture-in-Picture (PiP)**: botón dedicado; también entra automáticamente al salir de la app con música sonando (solo Android 8+).

### Cuenta y datos
- **Inicio de sesión con Google** (scope `youtube.readonly`), o continuar como invitado.
- Con sesión iniciada:
  - **Suscripciones** (pestaña): videos recientes de hasta 10 canales suscritos (3 videos por canal), ordenados por fecha de publicación.
  - **Perfil** (avatar): foto, nombre, correo, tus playlists (títulos) y "vistos recientemente".
  - **Cerrar sesión** desde el perfil o el menú ☰.
- **Historial de reproducción persistente** (máx. 50 videos) guardado en `SharedPreferences`; sobrevive al cierre de la app.
- **Cambiar tema** (menú ☰): ciclo Claro → Oscuro → Sigue al sistema; se guarda en `SharedPreferences`.

### Reproducción de audio (motor)
- Obtención del stream: **InnerTube directo** (perfil IOS en www.youtube.com, sin descifrado de firmas) → fallback → **NewPipeExtractor**.
- URLs cacheadas en memoria para el historial inmediato (anterior/siguiente sin espera).
- Precarga de la canción anterior y siguiente de la cola.
- Fix de compatibilidad: las URLs con restricción geográfica (`gcr`) rechazan rangos > 1 MB, por eso cada petición de stream se limita a 1 MB (ver `RangeFixingDataSource`).

---

## 3. Lo que NO se puede hacer (limitaciones actuales)

- **No hay video**: la app solo reproduce audio (decisión de diseño modo música). La `PlayerView` muestra la portada/fondo negro, no el video.
- **Botón "Crear" (lápiz)**: no implementado (solo muestra un aviso).
- **Subir video / likes / comentarios / compartir**: fuera de alcance.
- **Suscripciones sin sesión**: exige iniciar sesión con Google (scope readonly).
- **Página de canal, página de video, videos relacionados por canal**: no existe navegación a canales.
- **Historial de YouTube del servidor**: solo el historial local de la app.
- **Calidad/bajada de video**: solo se toma el mejor audio (bitrate más alto).
- **Playlists**: solo se muestran títulos en el perfil; no se pueden abrir ni reproducir.
- **Notificación de reproducción**: es básica (título + texto fijo), sin controles play/pausa/siguiente.
- **Modo PiP**: muestra el reproductor pero solo hay audio.
- **Persistencia de la cola**: la cola se pierde al cerrar la app (solo el historial es persistente).
- **Paginación del feed**: el feed carga una página; no hay scroll infinito ni "cargar más".
- **Región**: el feed popular usa `regionCode=MX`; algunos videos pueden estar bloqueados en la región (ej. `jfKfPfyJRdk` da UNPLAYABLE en Perú/MX).
- **Mantenimiento**: las versiones de cliente InnerTube (`ClientProfiles`) caducan; cuando dejen de funcionar hay que actualizarlas (ver sección 8).
- **Acentos mojibake**: algunos textos en `MainActivity.kt` muestran caracteres corruptos (ej. "Ya estÃ¡ en la cola" en vez de "Ya está en la cola") por un problema de codificación de archivos (UTF-8 mal decodificado). Es visible en diálogos. Pendiente de corregir.
- **Piped API**: `PipedApi.kt` apunta a `pipedapi.kavin.rocks` que hoy está caída; el flujo principal no depende de ella (el feed usa YouTube Data API y los streams usan InnerTube/NewPipe).

---

## 4. Estructura del proyecto

```
my tube/
├── app/
│   ├── build.gradle.kts                     # Dependencias y configuración de build
│   └── src/main/
│       ├── AndroidManifest.xml              # Permisos, actividades, servicio
│       ├── java/com/miappvideos/
│       │   ├── MainActivity.kt              # Actividad principal (todo el UI + lógica)
│       │   ├── MiAppVideosApplication.kt    # Inicializa NewPipeExtractor
│       │   ├── adapter/
│       │   │   ├── VideoAdapter.kt          # Tarjetas del feed
│       │   │   ├── QueueAdapter.kt          # Cola de reproducción
│       │   │   └── PlaylistAdapter.kt       # Playlists del perfil
│       │   ├── api/
│       │   │   ├── MusicStreamProvider.kt   # Orquestador de audio (InnerTube → fallback)
│       │   │   ├── StreamProvider.kt        # Cache de streams (NewPipeExtractor)
│       │   │   ├── NewPipeStreamExtractor.kt# Extrae streams con NewPipeExtractor
│       │   │   ├── NativeStreamExtractor.kt # Extracción HTML/InnerTube manual (respaldo)
│       │   │   ├── NewPipeDownloader.kt     # Puente OkHttp ↔ NewPipeExtractor
│       │   │   ├── PipedApi.kt              # Cliente Retrofit de Piped API (hoy caída)
│       │   │   ├── YouTubeApi.kt            # Cliente Retrofit de YouTube Data API v3
│       │   │   └── YouTubeDataManager.kt    # Lógica de Data API + refresh token
│       │   ├── api/innertube/
│       │   │   ├── ClientProfiles.kt        # Perfiles de cliente InnerTube (IOS, etc.)
│       │   │   └── RotatingHttpClient.kt    # OkHttp compartido (pool de proxies vacío)
│       │   ├── auth/
│       │   │   └── LoginActivity.kt         # Inicio de sesión con Google
│       │   ├── model/
│       │   │   ├── PipedVideo.kt            # Modelos Piped/streams
│       │   │   └── YouTubeModels.kt         # Modelos de YouTube Data API
│       │   └── player/
│       │       ├── ExoPlayerManager.kt      # Wrapper de ExoPlayer
│       │       ├── ExoPlayerHolder.kt       # Objeto global que comparte el player (en PlayerService.kt)
│       │       ├── PlayerService.kt         # Servicio de reproducción en segundo plano
│       │       └── RangeFixingDataSource.kt # Fix de rangos HTTP (1 MB máx) para googlevideo
│       └── res/
│           ├── drawable/                    # 24 vectores de iconos + bg_duration + launcher
│           ├── layout/                      # activity_main, activity_login, item_video,
│           │                                # item_queue, profile_dialog
│           ├── menu/bottom_nav_menu.xml     # Menú inferior (5 pestañas)
│           └── values/ + values-night/      # strings, colors, themes (claro/oscuro)
```

---

## 5. Flujo general de una reproducción

```
[Feed] --tap tarjeta--> MainActivity.playVideo(video)
  1. Actualiza títulos (mini + full player), muestra pantalla de reproducción.
  2. Agrega el video a la cola (si no estaba) y al historial (persistente, máx 50).
  3. MusicStreamProvider.getAudioStream(videoId)  ← en corrutina
        ├─ Intenta ClientProfiles (1º IOS en youtube.com, 2º WEB_MUSIC, ...)
        │      POST {apiBaseUrl}player?key=...  con contexto de cliente
        │      → playabilityStatus == "OK" y mejor formato audio/* con url
        ├─ Si todos fallan → StreamProvider → NewPipeStreamExtractor (NewPipeExtractor)
        └─ Cache en memoria (videoId → URL)
  4. ExoPlayerManager.playUrl(url)
        └─ ExoPlayer (media3 1.2.1)
             └─ RangeFixingDataSource (envuelve OkHttpDataSource)
                  • Convierte `Range: bytes=0-` en rango cerrado usando `clen`
                  • Limita cada rango a 1 MB (las URLs `gcr` dan 403 con rangos grandes)
  5. showMiniPlayer() → según el estado, pantalla completa o mini-reproductor.
  6. preloadAdjacent() → precarga anterior y siguiente de la cola en background.
```

---

## 6. Referencia de archivos (código completo + descripción)

> Orden de lectura sugerido: manifest → modelo → API → player → UI.

### 6.1 `app/build.gradle.kts`

**Qué hace:** define el módulo de la app: SDK 34, minSdk 24, package `com.miappvideos`, viewBinding y todas las dependencias.

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miappvideos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.miappvideos"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.1")

    // Retrofit + Gson para Piped API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coil para imágenes
    implementation("io.coil-kt:coil:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // RecyclerView + CardView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // YouTube Data API v3
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-youtube:v3-rev222-1.25.0")

    // Gson para JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // NewPipeExtractor
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.4")
    implementation("org.jsoup:jsoup:1.17.2")
}
```

**Vinculado con:** todas las clases del paquete `com.miappvideos`; `media3-datasource-okhttp` es el que permite `OkHttpDataSource` en `ExoPlayerManager`.

---

### 6.2 `app/src/main/AndroidManifest.xml`

**Qué hace:** declara permisos (Internet, foreground service de media, notificaciones, cuentas), la aplicación, las dos actividades y el servicio de reproducción.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" tools:targetApi="34" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" tools:targetApi="33" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
    <uses-permission android:name="android.permission.USE_CREDENTIALS" />

    <application
        android:name=".MiAppVideosApplication"
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MiAppVideos"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:supportsPictureInPicture="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".auth.LoginActivity"
            android:exported="true"
            android:theme="@style/Theme.MiAppVideos">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="@string/google_signin_scheme" />
            </intent-filter>
        </activity>

        <service
            android:name=".player.PlayerService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false"
            tools:targetApi="34">
            <intent-filter>
                <action android:name="android.media.session.MediaSessionService" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

**Vinculado con:** `MiAppVideosApplication` (inicializa NewPipe), `MainActivity` (launcher + PiP), `LoginActivity` (deep link `com.googleusercontent.apps.38145403059-lkn4onppsoqhe6sdlbpufnr9oqd2n22m://` del OAuth) y `PlayerService` (servicio foreground de tipo mediaPlayback). El `launchMode=singleTop` evita apilar MainActivity al volver desde el login.

---

### 6.3 `MainActivity.kt` — `java/com/miappvideos/`

**Qué hace:** la actividad principal. Orquesta TODO: feed, chips, búsqueda, cola, mini/full player, gestos, shuffle/repeat, historial persistente, PiP, tema, sesión, suscripciones y perfil. Es el archivo más grande (994 líneas).

```kotlin
package com.miappvideos

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintSet
import coil.load
import coil.transform.CircleCropTransformation
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.miappvideos.adapter.PlaylistAdapter
import com.miappvideos.adapter.VideoAdapter
import com.miappvideos.api.PipedApi
import com.miappvideos.api.YouTubeDataManager
import com.miappvideos.model.PipedVideo
import com.miappvideos.model.YouTubeVideo
import com.miappvideos.player.ExoPlayerHolder
import com.miappvideos.player.ExoPlayerManager
import com.miappvideos.player.PlayerService
import kotlin.math.abs
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerVideos: RecyclerView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var playerContainer: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var titleTextView: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnBackground: ImageButton
    private lateinit var btnPip: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnCreate: ImageButton
    private lateinit var btnAvatar: ImageView
    private lateinit var chipGroup: com.google.android.material.chip.ChipGroup
    private lateinit var chipsRow: HorizontalScrollView
    private lateinit var searchLayout: TextInputLayout
    private lateinit var searchInput: TextInputEditText
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rootLayout: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var queueRecyclerView: RecyclerView
    private lateinit var queueAdapter: com.miappvideos.adapter.QueueAdapter

    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniThumbnail: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniChannel: TextView
    private lateinit var miniPrev: ImageButton
    private lateinit var miniPlayPause: ImageButton
    private lateinit var miniNext: ImageButton
    private lateinit var miniExpand: ImageButton
    private lateinit var fullPlayerContainer: LinearLayout
    private lateinit var playerControlsBar: LinearLayout
    private lateinit var btnCollapse: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton

    private lateinit var api: PipedApi
    private lateinit var youTubeManager: YouTubeDataManager
    private lateinit var playerManager: ExoPlayerManager
    private lateinit var adapter: VideoAdapter

    private var isPlaying = false
    private var isInPipMode = false
    private var isBackgroundMode = false
    private var searchVisible = false
    private var isSignedIn = false
    private var currentEmail: String? = null
    private var currentName: String = "Invitado"
    private var currentPhotoUrl: String? = null
    private var isLoading = false
    private var refreshIndex = 0
    private var isExpanded = false
    private var isShuffle = false
    private var repeatMode = 0
    private val videoQueue = mutableListOf<com.miappvideos.model.PipedVideo>()
    private var currentQueueIndex = -1
    private val watchHistory = mutableListOf<com.miappvideos.model.PipedVideo>()

    companion object {
        private const val RC_LOGIN = 9002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        api = PipedApi.create()
        playerManager = ExoPlayerManager(this)
        ExoPlayerHolder.player = playerManager
        youTubeManager = YouTubeDataManager(this)

        currentName = intent.getStringExtra("user_name") ?: "Invitado"
        currentEmail = intent.getStringExtra("user_email")
        currentPhotoUrl = intent.getStringExtra("user_photo")
        isSignedIn = currentEmail != null

        bindViews()

        titleTextView.text = "Bienvenido, $currentName"

        if (isSignedIn) {
            lifecycleScope.launch {
                youTubeManager.refreshToken(currentEmail)
            }
        }
        setupPlayerView()
        setupRecyclerView()
        setupControls()
        setupSwipeRefresh()
        setupQueue()
        loadWatchHistory()
        loadAvatar()
        updatePipButtonVisibility()

        loadTrending()
    }

    override fun onResume() {
        super.onResume()
        loadTrending()
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        toolbar = findViewById(R.id.toolbar)
        recyclerVideos = findViewById(R.id.recyclerVideos)
        playerContainer = findViewById(R.id.playerContainer)
        playerView = findViewById(R.id.playerView)
        titleTextView = findViewById(R.id.titleTextView)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnBackground = findViewById(R.id.btnBackground)
        btnPip = findViewById(R.id.btnPip)
        btnSearch = findViewById(R.id.btnSearch)
        btnMenu = findViewById(R.id.btnMenu)
        btnCreate = findViewById(R.id.btnCreate)
        btnAvatar = findViewById(R.id.btnAvatar)
        chipGroup = findViewById(R.id.chipGroup)
        chipsRow = findViewById(R.id.chipsRow)
        searchLayout = findViewById(R.id.searchLayout)
        searchInput = findViewById(R.id.searchInput)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        queueRecyclerView = findViewById(R.id.queueRecyclerView)

        miniPlayer = findViewById(R.id.miniPlayer)
        miniThumbnail = findViewById(R.id.miniThumbnail)
        miniTitle = findViewById(R.id.miniTitle)
        miniChannel = findViewById(R.id.miniChannel)
        miniPrev = findViewById(R.id.miniPrev)
        miniPlayPause = findViewById(R.id.miniPlayPause)
        miniNext = findViewById(R.id.miniNext)
        miniExpand = findViewById(R.id.miniExpand)
        fullPlayerContainer = findViewById(R.id.fullPlayerContainer)
        playerControlsBar = findViewById(R.id.playerControlsBar)
        btnCollapse = findViewById(R.id.btnCollapse)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
    }

    private fun setupPlayerView() {
        playerView.player = playerManager.player

        playerManager.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlay: Boolean) {
                isPlaying = isPlay
                updatePlayPauseIcon()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playerContainer.visibility = View.VISIBLE
                } else if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val sb = StringBuilder("Playback error: ${error.message}\n")
                var cause: Throwable? = error
                while (cause != null) {
                    sb.append("  Caused by: ${cause.javaClass.simpleName}: ${cause.message}\n")
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        cause.responseBody?.let { sb.append("  RESPONSE BODY: ${String(it)}\n") }
                    }
                    cause = cause.cause
                }
                Log.d("PlayerError", sb.toString())
            }
        })

        miniPlayer.setOnClickListener { togglePlayerMode() }

        val swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isExpanded) {
                    togglePlayerMode()
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                return if (isExpanded) {
                    if (abs(dy) > abs(dx) && abs(dy) > 100 && velocityY > 400) {
                        togglePlayerMode()
                        true
                    } else {
                        false
                    }
                } else {
                    if (abs(dx) > abs(dy) && abs(dx) > 100 && abs(velocityX) > 400) {
                        if (velocityX < 0) nextVideo() else prevVideo()
                        true
                    } else {
                        false
                    }
                }
            }
        })
        miniPlayer.setOnTouchListener { _, event -> swipeDetector.onTouchEvent(event) }
        playerView.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            false
        }
        playerControlsBar.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            false
        }

        miniPlayPause.setOnClickListener {
            playerManager.isPlaying = !playerManager.isPlaying
        }

        miniPrev.setOnClickListener { prevVideo() }
        miniNext.setOnClickListener { nextVideo() }
        miniExpand.setOnClickListener { togglePlayerMode() }
        btnCollapse.setOnClickListener { togglePlayerMode() }
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter(
            emptyList(),
            onVideoClick = { video -> playVideo(video) },
            onOptionsClick = { video -> showVideoOptions(video) }
        )
        recyclerVideos.layoutManager = LinearLayoutManager(this)
        recyclerVideos.adapter = adapter
        recyclerVideos.setHasFixedSize(true)
    }

    private fun setupQueue() {
        queueAdapter = com.miappvideos.adapter.QueueAdapter(
            emptyList(),
            -1,
            onItemClick = { index -> playQueueItem(index) },
            onOptionsClick = { index -> showQueueOptions(index) }
        )
        queueRecyclerView.layoutManager = LinearLayoutManager(this)
        queueRecyclerView.adapter = queueAdapter
    }

    private fun refreshQueue() {
        queueAdapter.updateQueue(videoQueue.toList(), currentQueueIndex)
        if (currentQueueIndex >= 0) {
            queueRecyclerView.scrollToPosition(currentQueueIndex)
        }
    }

    private fun showVideoOptions(video: com.miappvideos.model.PipedVideo) {
        val options = arrayOf("Reproducir", "Agregar a la cola")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(video.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playVideo(video)
                    1 -> {
                        val videoId = extractVideoId(video.url.orEmpty())
                        if (videoQueue.none { extractVideoId(it.url.orEmpty()) == videoId }) {
                            videoQueue.add(video)
                            Toast.makeText(this, "Agregado a la cola", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Ya estÃ¡ en la cola", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun showQueueOptions(index: Int) {
        if (index < 0 || index >= videoQueue.size) return
        val options = arrayOf("Reproducir ahora", "Quitar de la cola")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(videoQueue[index].title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playQueueItem(index)
                    1 -> {
                        videoQueue.removeAt(index)
                        if (currentQueueIndex > index) currentQueueIndex--
                        else if (currentQueueIndex == index) currentQueueIndex = -1
                        refreshQueue()
                    }
                }
            }
            .show()
    }

    private fun showMenuDialog() {
        val items = mutableListOf("Cambiar tema")
        if (isSignedIn) items.add("Cerrar sesiÃ³n")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Opciones")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> cycleTheme()
                    1 -> logout()
                }
            }
            .show()
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            playerManager.isPlaying = !playerManager.isPlaying
        }

        btnBackground.setOnClickListener {
            startBackgroundPlayback()
        }

        btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            updateRepeatShuffleTint()
        }

        btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateRepeatShuffleTint()
        }

        btnPip.setOnClickListener {
            enterPipMode()
        }

        btnSearch.setOnClickListener {
            toggleSearch()
        }

        btnMenu.setOnClickListener {
            showMenuDialog()
        }

        btnCreate.setOnClickListener {
            Toast.makeText(this, "FunciÃ³n de crear aÃºn no disponible", Toast.LENGTH_SHORT).show()
        }

        btnAvatar.setOnClickListener {
            openAccount()
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            when (id) {
                R.id.chipTodo -> loadTrending()
                else -> searchVideos(categoryQuery(id))
            }
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    searchVideos(query)
                    searchLayout.visibility = View.GONE
                    searchVisible = false
                }
                true
            } else false
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadTrending()
                R.id.nav_trending -> loadTrending()
                R.id.nav_music -> searchVideos("music")
                R.id.nav_subs -> loadSubscriptions()
                R.id.nav_account -> openAccount()
            }
            if (item.itemId != R.id.nav_account) {
                leavePlaybackScreen()
            }
            true
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadTrending { swipeRefresh.isRefreshing = false }
        }
        swipeRefresh.setColorSchemeResources(
            com.google.android.material.R.color.design_default_color_primary,
            android.R.color.holo_green_dark,
            android.R.color.holo_orange_dark
        )
    }

    private fun categoryQuery(id: Int): String = when (id) {
        R.id.chipMusica -> "mÃºsica"
        R.id.chipMixes -> "mixes"
        R.id.chipVideojuegos -> "videojuegos"
        R.id.chipNoticias -> "noticias"
        R.id.chipDeportes -> "deportes"
        R.id.chipComedia -> "comedia"
        R.id.chipEducacion -> "educaciÃ³n"
        else -> "mÃºsica"
    }

    private fun loadAvatar() {
        if (currentPhotoUrl != null) {
            btnAvatar.load(currentPhotoUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        } else {
            btnAvatar.setImageResource(R.drawable.ic_account_circle)
        }
    }

    private fun toggleSearch() {
        searchVisible = !searchVisible
        searchLayout.visibility = if (searchVisible) View.VISIBLE else View.GONE
        if (searchVisible) {
            searchInput.requestFocus()
        }
    }

    private fun loadTrending(onComplete: (() -> Unit)? = null) {
        if (isLoading) {
            onComplete?.invoke()
            return
        }
        isLoading = true
        lifecycleScope.launch {
            try {
                val ytVideos = youTubeManager.getPopularVideos()
                if (ytVideos.isNotEmpty()) {
                    adapter.updateVideos(ytVideos.map { it.toPipedVideo() })
                    isLoading = false
                    onComplete?.invoke()
                    return@launch
                }
            } catch (_: Exception) {}

            val fallbackQueries = listOf("trending", "music", "viral", "pop", "new")
            val query = fallbackQueries[refreshIndex % fallbackQueries.size]
            refreshIndex++
            try {
                val result = api.search(query)
                if (result.items.isNotEmpty()) {
                    adapter.updateVideos(result.items.take(20))
                    isLoading = false
                    onComplete?.invoke()
                    return@launch
                }
            } catch (_: Exception) {}
            runOnUiThread { Toast.makeText(this@MainActivity, "No se pudieron cargar videos. Verifica que YouTube Data API estÃ© habilitada.", Toast.LENGTH_LONG).show() }
            isLoading = false
            onComplete?.invoke()
        }
    }

    private fun searchVideos(query: String) {
        lifecycleScope.launch {
            try {
                val ytVideos = youTubeManager.searchYouTube(query)
                if (ytVideos.isNotEmpty()) {
                    adapter.updateVideos(ytVideos.map { it.toPipedVideo() })
                    return@launch
                }
            } catch (_: Exception) {}

            try {
                val result = api.search(query)
                adapter.updateVideos(result.items.take(30))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error al buscar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSubscriptions() {
        if (!isSignedIn) {
            Toast.makeText(this, "Inicia sesiÃ³n para ver tus suscripciones", Toast.LENGTH_SHORT).show()
            openAccount()
            return
        }
        lifecycleScope.launch {
            val subs = youTubeManager.getSubscriptions()
            if (subs.isEmpty()) {
                Toast.makeText(this@MainActivity, "Sin suscripciones o error al cargar", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val allVideos = mutableListOf<com.miappvideos.model.YouTubeVideo>()
            val channelIds = subs.mapNotNull { it.snippet?.resourceId?.channelId }
            for (channelId in channelIds.take(10)) {
                val videos = youTubeManager.getChannelUploadsVideos(channelId)
                allVideos.addAll(videos.take(3))
            }

            val unique = LinkedHashMap<String, com.miappvideos.model.YouTubeVideo>()
            for (video in allVideos) {
                val id = video.snippet?.resourceId?.videoId ?: continue
                unique[id] = video
            }

            if (unique.isEmpty()) {
                Toast.makeText(this@MainActivity, "No se pudieron cargar videos de tus suscripciones", Toast.LENGTH_SHORT).show()
            } else {
                val pipedVideos = unique.values
                    .sortedByDescending { it.snippet?.publishedAt ?: "" }
                    .map { it.toPipedVideo() }
                adapter.updateVideos(pipedVideos)
                Toast.makeText(this@MainActivity, "${pipedVideos.size} videos de tus suscripciones", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAccount() {
        if (!isSignedIn) {
            val intent = Intent(this, com.miappvideos.auth.LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(R.layout.profile_dialog)
            .create()

        dialog.setOnShowListener {
            val avatar = dialog.findViewById<ImageView>(R.id.profileAvatar)!!
            val name = dialog.findViewById<TextView>(R.id.profileName)!!
            val email = dialog.findViewById<TextView>(R.id.profileEmail)!!
            val playlistRv = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.profilePlaylists)!!
            val historyRv = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.profileHistory)!!
            val btnLogout = dialog.findViewById<Button>(R.id.btnLogout)!!

            name.text = currentName
            email.text = currentEmail ?: ""
            if (currentPhotoUrl != null) {
                avatar.load(currentPhotoUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            }

            btnLogout.setOnClickListener {
                logout()
                dialog.dismiss()
            }

            lifecycleScope.launch {
                val playlists = youTubeManager.getPlaylists()
                if (playlists.isNotEmpty()) {
                    playlistRv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
                    playlistRv.adapter = PlaylistAdapter(playlists)
                }
            }

            if (watchHistory.isNotEmpty()) {
                historyRv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
                historyRv.adapter = com.miappvideos.adapter.VideoAdapter(
                    watchHistory.take(10),
                    onVideoClick = { video ->
                        dialog.dismiss()
                        playVideo(video)
                    }
                )
            }
        }

        dialog.show()
    }

    private fun logout() {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this,
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        ).signOut()
        isSignedIn = false
        currentEmail = null
        currentName = "Invitado"
        currentPhotoUrl = null
        titleTextView.text = "Bienvenido, Invitado"
        youTubeManager.setAccessToken(null)
        loadAvatar()
        Toast.makeText(this, "SesiÃ³n cerrada", Toast.LENGTH_SHORT).show()
    }

    private fun com.miappvideos.model.YouTubeVideo.toPipedVideo(): PipedVideo {
        val videoId = when {
            id?.isJsonObject == true -> id.asJsonObject.get("videoId")?.asString
            id?.isJsonPrimitive == true && id.asString.length == 11 -> id.asString
            else -> null
        } ?: snippet?.resourceId?.videoId ?: snippet?.title?.hashCode()?.toString() ?: ""
        val thumb = snippet?.thumbnails?.medium?.url ?: snippet?.thumbnails?.high?.url
        return PipedVideo(
            url = "https://www.youtube.com/watch?v=$videoId",
            title = snippet?.title ?: "Sin tÃ­tulo",
            thumbnail = thumb,
            uploaderName = snippet?.channelTitle,
            uploaderAvatar = null,
            uploadedDate = snippet?.publishedAt,
            shortDescription = null,
            duration = null,
            views = null,
            uploaderVerified = null,
            channelId = snippet?.channelId
        )
    }

    private fun playVideo(video: com.miappvideos.model.PipedVideo) {
        val videoId = extractVideoId(video.url ?: return)
        playerManager.currentVideoId = videoId
        playerManager.currentTitle = video.title
        playerManager.currentThumbnail = video.thumbnail
        titleTextView.text = video.title

        miniTitle.text = video.title
        miniChannel.text = video.uploaderName ?: ""

        isExpanded = true
        miniPlayer.visibility = View.GONE
        fullPlayerContainer.visibility = View.VISIBLE
        playerContainer.visibility = View.VISIBLE
        enterPlaybackScreen()

        if (video.thumbnail != null) {
            miniThumbnail.load(video.thumbnail)
        }

        val index = videoQueue.indexOfFirst { extractVideoId(it.url.orEmpty()) == videoId }
        if (index >= 0) {
            currentQueueIndex = index
        } else {
            videoQueue.add(video)
            currentQueueIndex = videoQueue.size - 1
        }

        watchHistory.removeAll { extractVideoId(it.url.orEmpty()) == videoId }
        watchHistory.add(0, video)
        if (watchHistory.size > 50) watchHistory.removeAt(watchHistory.lastIndex)
        saveWatchHistory()

        lifecycleScope.launch {
            val url = com.miappvideos.api.MusicStreamProvider.getAudioStream(videoId)
            if (url != null) {
                playerManager.playUrl(url)
                showMiniPlayer()
            } else {
                Toast.makeText(this@MainActivity, "No se pudo obtener el audio", Toast.LENGTH_SHORT).show()
            }
        }

        preloadAdjacent()
        refreshQueue()
    }

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            url.length == 11 -> url
            else -> url.takeLast(11)
        }
    }

    private fun loadWatchHistory() {
        val prefs = getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("history_json", null) ?: return
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val video = com.miappvideos.model.PipedVideo(
                    url = obj.optString("url").takeIf { it.isNotEmpty() },
                    title = obj.optString("title", "Sin tÃ­tulo"),
                    thumbnail = obj.optString("thumbnail").takeIf { it.isNotEmpty() },
                    uploaderName = obj.optString("uploaderName").takeIf { it.isNotEmpty() },
                    uploaderAvatar = obj.optString("uploaderAvatar").takeIf { it.isNotEmpty() },
                    uploadedDate = obj.optString("uploadedDate").takeIf { it.isNotEmpty() },
                    shortDescription = obj.optString("shortDescription").takeIf { it.isNotEmpty() },
                    duration = if (obj.isNull("duration")) null else obj.optLong("duration", 0).takeIf { it > 0 },
                    views = if (obj.isNull("views")) null else obj.optLong("views", 0).takeIf { it > 0 },
                    uploaderVerified = if (obj.isNull("uploaderVerified")) null else obj.optBoolean("uploaderVerified"),
                    channelId = obj.optString("channelId").takeIf { it.isNotEmpty() }
                )
                watchHistory.add(video)
            }
        } catch (_: Exception) {}
    }

    private fun saveWatchHistory() {
        val prefs = getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        for (video in watchHistory) {
            val obj = org.json.JSONObject()
            try {
                obj.put("url", video.url ?: "")
                obj.put("title", video.title)
                obj.put("thumbnail", video.thumbnail ?: "")
                obj.put("uploaderName", video.uploaderName ?: "")
                obj.put("uploaderAvatar", video.uploaderAvatar ?: "")
                obj.put("uploadedDate", video.uploadedDate ?: "")
                obj.put("shortDescription", video.shortDescription ?: "")
                obj.put("channelId", video.channelId ?: "")
                if (video.duration != null) obj.put("duration", video.duration) else obj.put("duration", org.json.JSONObject.NULL)
                if (video.views != null) obj.put("views", video.views) else obj.put("views", org.json.JSONObject.NULL)
                if (video.uploaderVerified != null) obj.put("uploaderVerified", video.uploaderVerified) else obj.put("uploaderVerified", org.json.JSONObject.NULL)
            } catch (_: Exception) {}
            arr.put(obj)
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    private fun startBackgroundPlayback() {
        isBackgroundMode = true
        val intent = Intent(this, PlayerService::class.java).apply {
            putExtra("video_id", playerManager.currentVideoId)
            putExtra("title", playerManager.currentTitle)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Reproduciendo en segundo plano", Toast.LENGTH_SHORT).show()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
            isInPipMode = true
        } else {
            Toast.makeText(this, "PiP no soportado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayPauseIcon() {
        val icon = if (isPlaying)
            R.drawable.ic_pause
        else
            R.drawable.ic_play_arrow
        btnPlayPause.setImageResource(icon)
        miniPlayPause.setImageResource(icon)
    }

    private fun enterPlaybackScreen() {
        isExpanded = true
        miniPlayer.visibility = View.GONE
        fullPlayerContainer.visibility = View.VISIBLE
        playerContainer.visibility = View.VISIBLE
        swipeRefresh.visibility = View.GONE
        chipsRow.visibility = View.GONE
        searchLayout.visibility = View.GONE
        searchVisible = false

        val cs = ConstraintSet()
        cs.clone(rootLayout)
        cs.clear(playerContainer.id, ConstraintSet.TOP)
        cs.connect(playerContainer.id, ConstraintSet.TOP, toolbar.id, ConstraintSet.BOTTOM)
        cs.connect(playerContainer.id, ConstraintSet.BOTTOM, bottomNavigation.id, ConstraintSet.TOP)
        cs.constrainHeight(playerContainer.id, ConstraintSet.MATCH_CONSTRAINT)
        cs.applyTo(rootLayout)
    }

    private fun leavePlaybackScreen() {
        isExpanded = false
        miniPlayer.visibility = View.VISIBLE
        fullPlayerContainer.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE
        chipsRow.visibility = View.VISIBLE

        val cs = ConstraintSet()
        cs.clone(rootLayout)
        cs.clear(playerContainer.id, ConstraintSet.TOP)
        cs.clear(playerContainer.id, ConstraintSet.BOTTOM)
        cs.connect(playerContainer.id, ConstraintSet.BOTTOM, bottomNavigation.id, ConstraintSet.TOP)
        cs.constrainHeight(playerContainer.id, ConstraintSet.WRAP_CONTENT)
        cs.applyTo(rootLayout)
    }

    private fun togglePlayerMode() {
        if (isExpanded) leavePlaybackScreen() else enterPlaybackScreen()
    }

    private fun showMiniPlayer() {
        playerContainer.visibility = View.VISIBLE
        if (isExpanded) enterPlaybackScreen() else leavePlaybackScreen()
    }

    private fun handlePlaybackEnded() {
        when (repeatMode) {
            2 -> {
                playerManager.player.seekTo(0)
                playerManager.isPlaying = true
            }
            1 -> nextVideo()
            else -> {
                if (currentQueueIndex < videoQueue.size - 1) {
                    nextVideo()
                } else {
                    playerManager.isPlaying = false
                }
            }
        }
    }

    private fun playQueueItem(index: Int) {
        if (index < 0 || index >= videoQueue.size) return
        currentQueueIndex = index
        playVideo(videoQueue[index])
    }

    private fun prevVideo() {
        if (videoQueue.isEmpty()) return
        val newIndex = if (currentQueueIndex > 0) currentQueueIndex - 1 else videoQueue.size - 1
        playQueueItem(newIndex)
    }

    private fun nextVideo() {
        if (videoQueue.isEmpty()) return
        val newIndex = if (isShuffle && videoQueue.size > 1) {
            var random = (0 until videoQueue.size).random()
            while (random == currentQueueIndex) random = (0 until videoQueue.size).random()
            random
        } else if (currentQueueIndex < videoQueue.size - 1) {
            currentQueueIndex + 1
        } else {
            0
        }
        playQueueItem(newIndex)
    }

    private fun updateRepeatShuffleTint() {
        val activeTint = android.content.res.ColorStateList.valueOf(
            themeColor(com.google.android.material.R.attr.colorPrimary)
        )
        val inactiveTint = android.content.res.ColorStateList.valueOf(
            themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        btnShuffle.imageTintList = if (isShuffle) activeTint else inactiveTint
        btnRepeat.imageTintList = when (repeatMode) {
            0 -> inactiveTint
            else -> activeTint
        }
        btnShuffle.alpha = if (isShuffle) 1f else 0.5f
        btnRepeat.alpha = if (repeatMode == 0) 0.5f else 1f
    }

    private fun themeColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }

    private fun preloadAdjacent() {
        val next = currentQueueIndex + 1
        val prev = currentQueueIndex - 1
        lifecycleScope.launch {
            if (next < videoQueue.size) {
                val nextId = extractVideoId(videoQueue[next].url.orEmpty())
                if (nextId.isNotEmpty()) com.miappvideos.api.MusicStreamProvider.preload(nextId)
            }
            if (prev >= 0) {
                val prevId = extractVideoId(videoQueue[prev].url.orEmpty())
                if (prevId.isNotEmpty()) com.miappvideos.api.MusicStreamProvider.preload(prevId)
            }
        }
    }

    private fun updatePipButtonVisibility() {
        btnPip.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            View.VISIBLE else View.GONE
    }

    private fun cycleTheme() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val newMode = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        prefs.edit().putInt("theme_mode", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
        recreate()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPipMode) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
            isInPipMode = true
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            recyclerVideos.visibility = View.GONE
        } else {
            recyclerVideos.visibility = View.VISIBLE
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPipMode && !isBackgroundMode) {
            playerManager.player.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isBackgroundMode) {
            stopService(Intent(this, PlayerService::class.java))
            playerManager.release()
        }
    }
}
```

**Vinculado con:** `activity_main.xml` (todos los `R.id`), `item_video.xml` (vía `VideoAdapter`), `item_queue.xml` (vía `QueueAdapter`), `profile_dialog.xml` (perfil), `ExoPlayerManager` + `ExoPlayerHolder` (reproducción), `MusicStreamProvider` (audio), `YouTubeDataManager` + `PipedApi` (feed), `LoginActivity` (sesión), `PlayerService` (segundo plano), `bottom_nav_menu.xml` (pestañas), `colors.xml`/`themes.xml` (tema) y los iconos `ic_*`.

**Detalle de sus funciones clave:**
| Función | Rol |
|---|---|
| `onCreate` | Arranca todo: player, listas, gestos, historial, feed |
| `setupPlayerView` | Conecta ExoPlayer a la vista; gestos de swipe; listener de errores con diagnóstico (tag `PlayerError`) |
| `playVideo` | Núcleo de la reproducción: cola, historial, obtener URL de audio, reproducir, precargar |
| `enterPlaybackScreen` / `leavePlaybackScreen` | Cambia `playerContainer` entre pantalla completa y mini-reproductor vía `ConstraintSet` |
| `handlePlaybackEnded` | Aplica el modo repetir (0/1/2) y avance de cola |
| `nextVideo` / `prevVideo` | Cambio de canción con shuffle |
| `loadSubscriptions` | Feed de suscripciones: channels → uploads → playlistItems (10 canales × 3 videos, ordenados por fecha) |
| `saveWatchHistory` / `loadWatchHistory` | Historial JSON persistente (SharedPreferences `history_prefs`) |
| `toPipedVideo` | Convierte un video de la Data API al modelo unificado `PipedVideo` |
| `cycleTheme` / `applySavedTheme` | Tema claro/oscuro/sistema persistente (`theme_prefs`) |

---

### 6.4 `MiAppVideosApplication.kt`

**Qué hace:** clase `Application` que inicializa NewPipeExtractor con un `Downloader` propio basado en OkHttp. Se ejecuta antes que cualquier Activity.

```kotlin
package com.miappvideos

import android.app.Application
import com.miappvideos.api.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class MiAppVideosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader())
    }
}
```

**Vinculado con:** `AndroidManifest.xml` (`android:name=".MiAppVideosApplication"`) y `NewPipeDownloader`.

---

### 6.5 Modelos — `model/PipedVideo.kt`

**Qué hace:** define los modelos del feed y de streams. `PipedVideo` es el modelo unificado que usa toda la UI (tarjetas, cola, historial). `PipedStreamResponse`/`AudioStream`/`VideoStream` describen los streams descargables.

```kotlin
package com.miappvideos.model

data class PipedSearchResponse(
    val items: List<PipedVideo>,
    val nextpage: String?
)

data class PipedVideo(
    val url: String?,
    val title: String,
    val thumbnail: String?,
    val uploaderName: String?,
    val uploaderAvatar: String?,
    val uploadedDate: String?,
    val shortDescription: String?,
    val duration: Long?,
    val views: Long?,
    val uploaderVerified: Boolean?,
    val channelId: String? = null
)

data class PipedStreamResponse(
    val title: String?,
    val description: String?,
    val uploadDate: String?,
    val uploaderUrl: String?,
    val uploaderName: String?,
    val uploaderAvatar: String?,
    val thumbnailUrl: String?,
    val duration: Long?,
    val views: Long?,
    val liked: Int?,
    val dislikes: Int?,
    val audioStreams: List<AudioStream>?,
    val videoStreams: List<VideoStream>?
)

data class AudioStream(
    val url: String?,
    val format: String?,
    val quality: String?,
    val mimeType: String?,
    val codec: String?,
    val bitrate: Int?,
    val initStart: Int?,
    val initEnd: Int?,
    val indexStart: Int?,
    val indexEnd: Int?
)

data class VideoStream(
    val url: String?,
    val format: String?,
    val quality: String?,
    val mimeType: String?,
    val codec: String?,
    val videoOnly: Boolean?,
    val initStart: Int?,
    val initEnd: Int?,
    val indexStart: Int?,
    val indexEnd: Int?,
    val bitrate: Int?,
    val width: Int?,
    val height: Int?,
    val fps: Int?
)
```

**Vinculado con:** `VideoAdapter`, `QueueAdapter`, `MusicStreamProvider`, `StreamProvider`, `NewPipeStreamExtractor`, `NativeStreamExtractor`, `MainActivity`.

---

### 6.6 Modelos — `model/YouTubeModels.kt`

**Qué hace:** modelos de la YouTube Data API v3 (suscripciones, playlists, videos, canales, miniaturas). Se mapean a `PipedVideo` con `toPipedVideo()`.

```kotlin
package com.miappvideos.model

// --- Subscriptions ---
data class YouTubeSubscriptionResponse(
    val items: List<YouTubeSubscription>?,
    val nextPageToken: String?,
    val pageInfo: PageInfo?
)

data class YouTubeSubscription(
    val id: String?,
    val snippet: SubscriptionSnippet?
)

data class SubscriptionSnippet(
    val title: String?,
    val description: String?,
    val resourceId: ResourceId?,
    val thumbnails: Thumbnails?,
    val channelId: String?
)

data class ResourceId(
    val channelId: String?
)

// --- Playlists ---
data class YouTubePlaylistResponse(
    val items: List<YouTubePlaylist>?,
    val nextPageToken: String?,
    val pageInfo: PageInfo?
)

data class YouTubePlaylist(
    val id: String?,
    val snippet: PlaylistSnippet?
)

data class PlaylistSnippet(
    val title: String?,
    val description: String?,
    val thumbnails: Thumbnails?,
    val channelId: String?,
    val channelTitle: String?
)

// --- Videos (search results, playlist items, popular) ---
data class YouTubeVideoResponse(
    val items: List<YouTubeVideo>?,
    val nextPageToken: String?,
    val pageInfo: PageInfo?
)

data class YouTubeVideo(
    val id: com.google.gson.JsonElement?,
    val snippet: VideoSnippet?
)

data class VideoSnippet(
    val title: String?,
    val description: String?,
    val thumbnails: Thumbnails?,
    val channelId: String?,
    val channelTitle: String?,
    val publishedAt: String?,
    val resourceId: VideoResourceId?
)

data class VideoResourceId(
    val videoId: String?
)

// --- Channel ---
data class YouTubeChannelResponse(
    val items: List<YouTubeChannel>?,
    val pageInfo: PageInfo?
)

data class YouTubeChannel(
    val id: String?,
    val snippet: ChannelSnippet?,
    val statistics: ChannelStatistics?,
    val contentDetails: ChannelContentDetails?
)

data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists?
)

data class RelatedPlaylists(
    val uploads: String?,
    val favorites: String?,
    val watchHistory: String?,
    val watchLater: String?
)

data class ChannelSnippet(
    val title: String?,
    val description: String?,
    val thumbnails: Thumbnails?,
    val customUrl: String?
)

data class ChannelStatistics(
    val subscriberCount: String?,
    val videoCount: String?,
    val viewCount: String?
)

// --- Shared ---
data class Thumbnails(
    val default: ThumbnailInfo?,
    val medium: ThumbnailInfo?,
    val high: ThumbnailInfo?
)

data class ThumbnailInfo(
    val url: String?,
    val width: Int?,
    val height: Int?
)

data class PageInfo(
    val totalResults: Int?,
    val resultsPerPage: Int?
)
```

**Vinculado con:** `YouTubeApi` (Retrofit) y `YouTubeDataManager`.

---

### 6.7 API — `api/YouTubeApi.kt`

**Qué hace:** interfaz Retrofit para la YouTube Data API v3. Un interceptor de OkHttp añade la `key` de API a cada petición y, si hay sesión, el `Authorization: Bearer <token>`. Usa `accessToken` renovado por `YouTubeDataManager.refreshToken`.

```kotlin
package com.miappvideos.api

import com.miappvideos.model.YouTubeChannelResponse
import com.miappvideos.model.YouTubePlaylistResponse
import com.miappvideos.model.YouTubeSubscriptionResponse
import com.miappvideos.model.YouTubeVideoResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {

    @GET("subscriptions")
    suspend fun getSubscriptions(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 25
    ): YouTubeSubscriptionResponse

    @GET("playlists")
    suspend fun getPlaylists(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 25
    ): YouTubePlaylistResponse

    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 25
    ): YouTubeVideoResponse

    @GET("videos")
    suspend fun getVideos(
        @Query("part") part: String = "snippet",
        @Query("chart") chart: String = "mostPopular",
        @Query("regionCode") regionCode: String = "MX",
        @Query("maxResults") maxResults: Int = 20
    ): YouTubeVideoResponse

    @GET("channels")
    suspend fun getChannel(
        @Query("part") part: String = "snippet",
        @Query("id") channelId: String
    ): YouTubeChannelResponse

    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String? = null,
        @Query("channelId") channelId: String? = null,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20
    ): YouTubeVideoResponse

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"

        fun create(apiKey: String, accessToken: String?): YouTubeApi {
            val client = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val url = original.url.newBuilder()
                        .addQueryParameter("key", apiKey)
                        .build()
                    val request = if (accessToken != null) {
                        original.newBuilder()
                            .url(url)
                            .header("Authorization", "Bearer $accessToken")
                            .build()
                    } else {
                        original.newBuilder()
                            .url(url)
                            .build()
                    }
                    chain.proceed(request)
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YouTubeApi::class.java)
        }
    }
}
```

**Vinculado con:** `YouTubeDataManager` (único usuario), `YouTubeModels.kt`, y la API key `youtube_api_key` de `strings.xml`.

---

### 6.8 API — `api/YouTubeDataManager.kt`

**Qué hace:** capa intermedia entre la UI y la Data API. Expone métodos suspendidos (popular, búsqueda, suscripciones, playlists, uploads de canal) y renueva el token OAuth con `GoogleAuthUtil.getToken` (scope `youtube.readonly`).

```kotlin
package com.miappvideos.api

import android.content.Context
import com.miappvideos.model.YouTubeSubscription
import com.miappvideos.model.YouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeDataManager(private val context: Context) {

    private var accessToken: String? = null

    private val apiKey: String
        get() = context.getString(com.miappvideos.R.string.youtube_api_key)

    fun setAccessToken(token: String?) {
        accessToken = token
    }

    private fun createApi() = YouTubeApi.create(apiKey, accessToken)

    suspend fun getSubscriptions(): List<YouTubeSubscription> = withContext(Dispatchers.IO) {
        try {
            createApi().getSubscriptions().items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPlaylists(): List<com.miappvideos.model.YouTubePlaylist> = withContext(Dispatchers.IO) {
        try {
            createApi().getPlaylists().items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChannelUploadsVideos(channelId: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val channel = createApi().getChannel(part = "contentDetails", channelId = channelId)
                .items?.firstOrNull()
            val uploadsId = channel?.contentDetails?.relatedPlaylists?.uploads ?: return@withContext emptyList()
            createApi().getPlaylistItems(playlistId = uploadsId, maxResults = 10).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularVideos(region: String = "MX"): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            createApi().getVideos(regionCode = region).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchYouTube(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            createApi().search(query = query).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchYouTubeByChannel(channelId: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            createApi().search(query = null, channelId = channelId).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun refreshToken(accountName: String?) {
        if (accountName == null) {
            accessToken = null
            return
        }
        try {
            val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                context,
                accountName,
                "oauth2:https://www.googleapis.com/auth/youtube.readonly"
            )
            accessToken = token
        } catch (e: Exception) {
            accessToken = null
        }
    }
}
```

**Vinculado con:** `MainActivity` (feed, suscripciones, perfil), `LoginActivity` (sesión), `YouTubeApi` y `strings.xml` (API key).

---

### 6.9 API — `api/PipedApi.kt`

**Qué hace:** cliente Retrofit para una instancia pública de Piped API (mirror de YouTube). Hoy la instancia `pipedapi.kavin.rocks` está caída; queda como respaldo futuro y para `trending`.

```kotlin
package com.miappvideos.api

import com.miappvideos.model.PipedSearchResponse
import com.miappvideos.model.PipedStreamResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

interface PipedApi {

    @GET("search")
    suspend fun search(@Query("q") query: String): PipedSearchResponse

    @GET("streams/{videoId}")
    suspend fun getStreams(@Path("videoId") videoId: String): PipedStreamResponse

    @GET("trending")
    suspend fun trending(): PipedSearchResponse

    companion object {
        private const val BASE_URL = "https://pipedapi.kavin.rocks/"

        fun create(): PipedApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PipedApi::class.java)
        }
    }
}
```

**Vinculado con:** `MainActivity.loadTrending` (fallback cuando la Data API falla), `searchVideos` (fallback) y `PipedVideo.kt`.

---

### 6.10 API — `api/MusicStreamProvider.kt` (el corazón del modo música)

**Qué hace:** obtiene el mejor stream de AUDIO para un video. Primero prueba InnerTube directo con los perfiles de `ClientProfiles` (orden: IOS → WEB_MUSIC → ANDROID_MUSIC → IOS_MUSIC). El perfil IOS devuelve URLs de audio listas para usar (sin descifrado de firma). Si todos fallan, usa `StreamProvider` (NewPipeExtractor). Cachea por videoId en memoria.

```kotlin
package com.miappvideos.api

import android.util.Log
import com.miappvideos.api.innertube.ClientProfile
import com.miappvideos.api.innertube.ClientProfiles
import com.miappvideos.api.innertube.RotatingHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Orquestador de streams enfocado en audio (estilo OpenTune, codigo propio).
 *
 * Orden de resolucion:
 *  1. InnerTube directo (music.youtube.com) probando los 3 perfiles de ClientProfiles.
 *  2. Fallback al StreamProvider existente (Piped + NewPipeExtractor).
 *
 * Solo se extrae audio (la app es de musica); los resultados se ordenan por
 * bitrate descendente.
 */
object MusicStreamProvider {

    private const val TAG = "MusicStreamProvider"
    private const val JSON_TYPE = "application/json; charset=utf-8"
    private val cache = mutableMapOf<String, String?>()

    suspend fun getAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        cache[videoId] ?: run {
            val result = fetchFromInnerTube(videoId) ?: fetchFromExistingProvider(videoId)
            cache[videoId] = result
            result
        }
    }

    suspend fun preload(vararg videoIds: String) {
        for (id in videoIds) {
            if (id !in cache) {
                val url = fetchFromInnerTube(id) ?: fetchFromExistingProvider(id)
                cache[id] = url
            }
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private fun fetchFromInnerTube(videoId: String): String? {
        for (profile in ClientProfiles.all) {
            try {
                val url = requestAudio(profile, videoId)
                if (url != null) {
                    Log.d(TAG, "InnerTube OK con perfil ${profile.name} para $videoId")
                    return url
                }
            } catch (e: Exception) {
                Log.d(TAG, "Perfil ${profile.name} fallo para $videoId: ${e.message}")
            }
        }
        return null
    }

    private fun requestAudio(profile: ClientProfile, videoId: String): String? {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", profile.clientName)
                        .put("clientVersion", profile.clientVersion)
                        .put("hl", "es")
                        .put("gl", "MX")
                        .put("userAgent", profile.userAgent)
                )
            )
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()

        val request = Request.Builder()
            .url("${profile.apiBaseUrl}player?key=${profile.apiKey}")
            .header("User-Agent", profile.userAgent)
            .header("Origin", profile.origin)
            .header("Referer", profile.referer)
            .post(body.toRequestBody(JSON_TYPE.toMediaType()))
            .build()

        RotatingHttpClient.client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)

            val status = json.optJSONObject("playabilityStatus")?.optString("status")
            if (status != "OK") {
                Log.d(TAG, "Playability para $videoId ($profile): $status")
                return null
            }

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            var best: Pair<Int, String>? = null
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType", "")
                if (!mimeType.startsWith("audio/")) continue
                val url = format.optString("url", "")
                if (url.isEmpty()) continue
                val bitrate = format.optInt("bitrate", 0)
                if (best == null || bitrate > best!!.first) {
                    best = bitrate to url
                }
            }
            val url = best?.second
            return url
        }
    }

    private suspend fun fetchFromExistingProvider(videoId: String): String? {
        return try {
            val streams = StreamProvider.getStreams(videoId) ?: return null
            streams.audioStreams?.maxByOrNull { it.bitrate ?: 0 }?.url
        } catch (e: Exception) {
            Log.d(TAG, "Fallback fallo para $videoId: ${e.message}")
            null
        }
    }
}
```

**Vinculado con:** `ClientProfiles` (perfiles), `RotatingHttpClient` (HTTP), `StreamProvider` (fallback), `MainActivity.playVideo`/`preloadAdjacent`.

---

### 6.11 API — `api/StreamProvider.kt`

**Qué hace:** cache simple de streams completos (audio+video) usando `NewPipeStreamExtractor`. Es el plan B de `MusicStreamProvider`.

```kotlin
package com.miappvideos.api

import com.miappvideos.model.PipedStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StreamProvider {

    private val cache = mutableMapOf<String, PipedStreamResponse?>()

    suspend fun getStreams(videoId: String): PipedStreamResponse? = withContext(Dispatchers.IO) {
        cache[videoId] ?: run {
            val result = NewPipeStreamExtractor.getStreams(videoId)
            cache[videoId] = result
            result
        }
    }

    suspend fun preload(vararg videoIds: String) {
        for (id in videoIds) {
            if (id !in cache) {
                val result = NewPipeStreamExtractor.getStreams(id)
                cache[id] = result
            }
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
```

**Vinculado con:** `MusicStreamProvider` y `NewPipeStreamExtractor`.

---

### 6.12 API — `api/NewPipeStreamExtractor.kt`

**Qué hace:** extrae streams con la librería NewPipeExtractor (TeamNewPipe). Convierte `StreamInfo` (audio, video y video-only) al modelo propio.

```kotlin
package com.miappvideos.api

import android.util.Log
import com.miappvideos.model.AudioStream
import com.miappvideos.model.PipedStreamResponse
import com.miappvideos.model.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo

object NewPipeStreamExtractor {

    private const val TAG = "NewPipeStreamExtractor"

    suspend fun getStreams(videoId: String): PipedStreamResponse? = withContext(Dispatchers.IO) {
        try {
            val service = NewPipe.getService("YouTube")
            Log.d(TAG, "Service loaded: ${service.serviceInfo.name}")
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            Log.d(TAG, "Extractor created, fetching page...")
            val info = StreamInfo.getInfo(extractor)
            Log.d(TAG, "StreamInfo obtained: ${info.name}")

            info.errors.forEach { err ->
                Log.w(TAG, "StreamInfo error: ${err.message}", err)
            }

            val audioStreams = info.audioStreams.orEmpty().mapNotNull { audio ->
                val url = audio.content
                val codec = audio.codec
                val mimeType = audio.format?.mimeType?.let {
                    if (codec != null) "$it; codecs=\"$codec\"" else it
                } ?: "audio/mp4"
                AudioStream(
                    url = url,
                    format = audio.format?.name,
                    quality = if (audio.averageBitrate > 0) "${audio.averageBitrate / 1000}kbps" else null,
                    mimeType = mimeType,
                    codec = codec,
                    bitrate = audio.averageBitrate.takeIf { it > 0 },
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null
                )
            }
            Log.d(TAG, "Audio streams extracted: ${audioStreams.size}")

            val videoStreams = mutableListOf<VideoStream>()

            info.videoStreams.orEmpty().forEach { video ->
                videoStreams.add(VideoStream(
                    url = video.content,
                    format = video.format?.name,
                    quality = video.quality,
                    mimeType = video.format?.mimeType ?: "video/mp4",
                    codec = video.codec,
                    videoOnly = video.isVideoOnly(),
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                    bitrate = video.bitrate.takeIf { it > 0 },
                    width = null, height = null, fps = null
                ))
            }
            Log.d(TAG, "Video streams extracted: ${videoStreams.size}")

            info.videoOnlyStreams.orEmpty().forEach { video ->
                videoStreams.add(VideoStream(
                    url = video.content,
                    format = video.format?.name,
                    quality = video.quality,
                    mimeType = video.format?.mimeType ?: "video/mp4",
                    codec = video.codec,
                    videoOnly = true,
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                    bitrate = video.bitrate.takeIf { it > 0 },
                    width = null, height = null, fps = null
                ))
            }
            Log.d(TAG, "Total video streams (incl. video-only): ${videoStreams.size}")

            return@withContext PipedStreamResponse(
                title = info.name,
                description = null, uploadDate = null, uploaderUrl = null,
                uploaderName = info.uploaderName,
                uploaderAvatar = null, thumbnailUrl = null, duration = info.duration,
                views = info.viewCount, liked = null, dislikes = null,
                audioStreams = audioStreams, videoStreams = videoStreams
            )
        } catch (e: Exception) {
            Log.e(TAG, "getStreams failed for videoId=$videoId", e)
            null
        }
    }
}
```

**Vinculado con:** `StreamProvider` y `NewPipeDownloader` (necesita `NewPipe.init` hecho en `MiAppVideosApplication`).

---

### 6.13 API — `api/NewPipeDownloader.kt`

**Qué hace:** adaptador que NewPipeExtractor necesita para hacer HTTP; traduce entre las clases `Request`/`Response` de NewPipe y OkHttp.

```kotlin
package com.miappvideos.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: NPRequest): NPResponse {
        val okRequest = buildOkRequest(request)
        val response = client.newCall(okRequest).execute()
        return toNPResponse(response)
    }

    private fun buildOkRequest(request: NPRequest): Request {
        val builder = Request.Builder().url(request.url())
        val headers = request.headers() ?: emptyMap()
        for ((key, values) in headers) {
            values.forEach { builder.header(key, it) }
        }
        val userAgent = headers["User-Agent"]?.firstOrNull()
            ?: "Mozilla/5.0 (Linux; Android 14) NewPipeExtractor"
        builder.header("User-Agent", userAgent)

        val data = request.dataToSend()
        val bodyString = data?.let { String(it) } ?: ""
        when (request.httpMethod()) {
            "POST" -> builder.post(bodyString.toRequestBody(null))
            "HEAD" -> builder.head()
            "PATCH" -> builder.patch(bodyString.toRequestBody(null))
            "DELETE" -> builder.delete(bodyString.toRequestBody(null))
            "PUT" -> builder.put(bodyString.toRequestBody(null))
            else -> builder.get()
        }
        return builder.build()
    }

    private fun toNPResponse(response: Response): NPResponse {
        val body = response.body?.string() ?: ""
        val headers = mutableMapOf<String, MutableList<String>>()
        response.headers.forEach { header ->
            headers.getOrPut(header.first) { mutableListOf() }.add(header.second)
        }
        return NPResponse(
            response.code,
            response.message,
            headers,
            body,
            response.request.url.toString()
        )
    }
}
```

**Vinculado con:** `MiAppVideosApplication` (NewPipe.init) y NewPipeExtractor.

---

### 6.14 API — `api/NativeStreamExtractor.kt` (extractor de respaldo)

**Qué hace:** extractor manual que descarga la página `watch?v=` y busca `ytInitialPlayerResponse` (con un parser JSON de llaves balanceadas), o el `player_response` dentro de los `<script>`, o directamente llama a InnerTube con claves/versiones conocidas. Queda como respaldo de emergencia.

```kotlin
package com.miappvideos.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.miappvideos.model.AudioStream
import com.miappvideos.model.PipedStreamResponse
import com.miappvideos.model.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object NativeStreamExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getStreams(videoId: String): PipedStreamResponse? = withContext(Dispatchers.IO) {
        try {
            fetchPlayerJson(videoId)?.let { parseStreams(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchPlayerJson(videoId: String): String? {
        val urls = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            "https://m.youtube.com/watch?v=$videoId",
            "https://youtube.com/watch?v=$videoId"
        )
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: continue
                    val result = extractJson(html) ?: extractFromScripts(html, videoId)
                    if (result != null) return result
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractJson(html: String): String? {
        val markers = listOf(
            "var ytInitialPlayerResponse = ",
            "window.ytInitialPlayerResponse = ",
            "ytInitialPlayerResponse = "
        )
        for (marker in markers) {
            val start = html.indexOf(marker)
            if (start == -1) continue
            val jsonStart = start + marker.length
            var depth = 0
            var inString = false
            var escape = false
            var end = jsonStart

            for (i in jsonStart until html.length) {
                val c = html[i]
                if (escape) { escape = false; continue }
                if (c == '\\' && inString) { escape = true; continue }
                if (c == '"') { inString = !inString; continue }
                if (!inString) {
                    if (c == '{') depth++
                    if (c == '}') {
                        depth--
                        if (depth == 0) { end = i + 1; break }
                    }
                }
            }
            if (end > jsonStart) return html.substring(jsonStart, end)
        }
        return null
    }

    private fun extractFromScripts(html: String, videoId: String): String? {
        val scriptRegex = Regex("<script[^>]*>([\\s\\S]*?)</script>")
        val matches = scriptRegex.findAll(html)

        for (match in matches) {
            val scriptContent = match.groupValues[1]
            if ("player_response" in scriptContent) {
                val respMatch = Regex("\"player_response\"\\s*:\\s*\"([^\"]+)\"").find(scriptContent)
                if (respMatch != null) {
                    val escaped = respMatch.groupValues[1]
                    return escaped.replace("\\u0026", "&")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\/", "/")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                }
            }
            if ("INNERTUBE_API_KEY" in scriptContent) {
                val keyMatch = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([^\"]+)\"").find(scriptContent)
                val clientMatch = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"").find(scriptContent)
                if (keyMatch != null && clientMatch != null) {
                    val apiKey = keyMatch.groupValues[1]
                    val clientVersion = clientMatch.groupValues[1]
                    return callInnerTube(videoId, apiKey, clientVersion)
                }
            }
        }
        return fetchViaInnerTube(videoId)
    }

    private val fallbackKeys = listOf(
        "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" to "2.20240701.00.00",
        "AIzaSyA8eiZmM1FaDVjRy-Df1t1Zq2y4oZ1K1Xg" to "19.09.37"
    )

    private fun fetchViaInnerTube(videoId: String): String? {
        for ((apiKey, clientVersion) in fallbackKeys) {
            val result = callInnerTube(videoId, apiKey, clientVersion)
            if (result != null) return result
        }
        return null
    }

    private fun callInnerTube(videoId: String, apiKey: String, clientVersion: String): String? {
        try {
            val payload = """{"videoId":"$videoId","context":{"client":{"clientName":"WEB","clientVersion":"$clientVersion"}}}"""
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) return response.body?.string()
        } catch (_: Exception) {}
        return null
    }

    private fun parseStreams(playerJson: String): PipedStreamResponse? {
        try {
            val root = JsonParser.parseString(playerJson).asJsonObject

            // Check playability
            val playabilityStatus = root.getAsJsonObject("playabilityStatus")
            if (playabilityStatus != null) {
                val status = playabilityStatus.get("status")?.asString ?: ""
                if (status != "OK") return null
            }

            val streamingData = root.getAsJsonObject("streamingData") ?: return null
            val audioFormats = mutableListOf<AudioStream>()
            val videoFormats = mutableListOf<VideoStream>()

            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
            adaptiveFormats?.forEach { element ->
                val format = element.asJsonObject
                val url = format.get("url")?.asString ?: return@forEach
                val mimeType = format.get("mimeType")?.asString ?: ""
                val bitrate = format.get("bitrate")?.asInt

                if (mimeType.startsWith("audio")) {
                    audioFormats.add(AudioStream(
                        url = url,
                        format = format.get("audioQuality")?.asString,
                        quality = format.get("audioQuality")?.asString,
                        mimeType = mimeType,
                        codec = format.get("audioCodec")?.asString,
                        bitrate = bitrate,
                        initStart = null, initEnd = null, indexStart = null, indexEnd = null
                    ))
                } else {
                    videoFormats.add(VideoStream(
                        url = url,
                        format = format.get("qualityLabel")?.asString,
                        quality = format.get("qualityLabel")?.asString,
                        mimeType = mimeType,
                        codec = format.get("videoCodec")?.asString,
                        videoOnly = !mimeType.contains("audio"),
                        initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                        bitrate = bitrate,
                        width = format.get("width")?.asInt,
                        height = format.get("height")?.asInt,
                        fps = format.get("fps")?.asInt
                    ))
                }
            }

            val formats = streamingData.getAsJsonArray("formats")
            formats?.forEach { element ->
                val format = element.asJsonObject
                val url = format.get("url")?.asString ?: return@forEach
                val mimeType = format.get("mimeType")?.asString ?: ""
                videoFormats.add(VideoStream(
                    url = url,
                    format = format.get("qualityLabel")?.asString,
                    quality = format.get("qualityLabel")?.asString,
                    mimeType = mimeType,
                    codec = format.get("videoCodec")?.asString,
                    videoOnly = false,
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                    bitrate = format.get("bitrate")?.asInt,
                    width = format.get("width")?.asInt,
                    height = format.get("height")?.asInt,
                    fps = format.get("fps")?.asInt
                ))
            }

            val videoDetails = root.getAsJsonObject("videoDetails")
            return PipedStreamResponse(
                title = videoDetails?.get("title")?.asString,
                description = null, uploadDate = null, uploaderUrl = null,
                uploaderName = videoDetails?.get("author")?.asString,
                uploaderAvatar = null, thumbnailUrl = null, duration = null,
                views = null, liked = null, dislikes = null,
                audioStreams = audioFormats, videoStreams = videoFormats
            )
        } catch (_: Exception) {
            return null
        }
    }
}
```

**Vinculado con:** no está en el flujo principal actual (era el extractor original); puede conectarse desde `StreamProvider` si NewPipe falla. Modelos `PipedStreamResponse`.

---

### 6.15 InnerTube — `api/innertube/ClientProfiles.kt`

**Qué hace:** define los perfiles de "identidad" con los que la app se presenta al endpoint `youtubei/v1/player` de YouTube. Orden verificado (jul 2026): **IOS en www.youtube.com es el que funciona** (devuelve URLs directas, sin firmas que descifrar); WEB_REMIX da UNPLAYABLE sin poToken; ANDROID_MUSIC/IOS_MUSIC piden login. Los `clientVersion` caducan y hay que actualizarlos.

```kotlin
package com.miappvideos.api.innertube

data class ClientProfile(
    val name: String,
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val apiBaseUrl: String,
    val apiKey: String,
    val origin: String,
    val referer: String
)

/**
 * Perfiles de cliente InnerTube (multi-identidad).
 * Si un perfil falla, MusicStreamProvider prueba el siguiente.
 *
 * Orden actual verificado (jul 2026):
 *  1. IOS en www.youtube.com -> devuelve URLs de audio directas (sin firma).
 *  2. WEB_REMIX (YouTube Music web).
 *  3. ANDROID_MUSIC / IOS_MUSIC (requieren login hoy en dia; se quedan por si vuelven a servir).
 *
 * MANTENIMIENTO: cuando los streams dejen de resolverse en todos los perfiles,
 * actualizar estos clientVersion (se encuentran en foros de NewPipe/Piped o
 * inspeccionando el trafico de la app oficial con HTTP Toolkit).
 */
object ClientProfiles {

    private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val YT_BASE = "https://www.youtube.com/youtubei/v1/"
    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1/"
    private const val UA_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    val all: List<ClientProfile> = listOf(
        ClientProfile(
            name = "IOS",
            clientName = "IOS",
            clientVersion = "21.03.1",
            userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
            apiBaseUrl = YT_BASE,
            apiKey = INNERTUBE_API_KEY,
            origin = "https://www.youtube.com",
            referer = "https://www.youtube.com/"
        ),
        ClientProfile(
            name = "WEB_MUSIC",
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            userAgent = UA_WEB,
            apiBaseUrl = MUSIC_BASE,
            apiKey = INNERTUBE_API_KEY,
            origin = "https://music.youtube.com",
            referer = "https://music.youtube.com/"
        ),
        ClientProfile(
            name = "ANDROID_MUSIC",
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.03.52",
            userAgent = "com.google.android.apps.youtube.music/7.03.52 (Linux; U; Android 14) gzip",
            apiBaseUrl = MUSIC_BASE,
            apiKey = INNERTUBE_API_KEY,
            origin = "https://music.youtube.com",
            referer = "https://music.youtube.com/"
        ),
        ClientProfile(
            name = "IOS_MUSIC",
            clientName = "IOS_MUSIC",
            clientVersion = "6.30.1",
            userAgent = "com.google.ios.youtubemusic/6.30.1 (iPhone14,3; U; CPU iOS 17_5 like Mac OS X;)",
            apiBaseUrl = MUSIC_BASE,
            apiKey = INNERTUBE_API_KEY,
            origin = "https://music.youtube.com",
            referer = "https://music.youtube.com/"
        )
    )
}
```

**Vinculado con:** `MusicStreamProvider.requestAudio` (recorre `ClientProfiles.all`).

---

### 6.16 InnerTube — `api/innertube/RotatingHttpClient.kt`

**Qué hace:** cliente OkHttp compartido por todo el stack InnerTube. El pool de proxies está vacío a propósito (proxies gratuitos = inseguros y bloqueados); `Proxy.NO_PROXY` garantiza conexión directa. Tiene timeouts de 15s/25s.

```kotlin
package com.miappvideos.api.innertube

import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Pool de proxies para rotacion. VACIO a proposito: no uses proxies
 * publicos gratuitos (inseguros y bloqueados por YouTube). Si no tienes
 * proxies propios (VPS con Squid, etc.), dejalo vacio: se usa conexion
 * directa, que sigue funcionando.
 */
object ProxyPool {
    val proxies: List<Proxy> = emptyList()
}

object RotatingHttpClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .proxySelector(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI?): List<Proxy> =
                if (ProxyPool.proxies.isEmpty()) listOf(Proxy.NO_PROXY)
                else listOf(ProxyPool.proxies.random())

            override fun connectFailed(
                uri: java.net.URI?,
                sa: java.net.SocketAddress?,
                ioe: java.io.IOException?
            ) {
            }
        })
        .build()

    fun client(): OkHttpClient = client
}
```

**Vinculado con:** `MusicStreamProvider` y `ExoPlayerManager` (el ExoPlayer baja los streams con este mismo cliente vía `OkHttpDataSource`).

---

### 6.17 Autenticación — `auth/LoginActivity.kt`

**Qué hace:** pantalla de inicio de sesión con Google (botón rojo + "Continuar como invitado"). Pide email, perfil, el scope `youtube.readonly` y el token ID. Si ya hay sesión, salta directo a MainActivity. Pasa nombre/correo/foto por extras del Intent.

```kotlin
package com.miappvideos.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.miappvideos.MainActivity
import com.miappvideos.R

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/youtube.readonly"))
            .requestIdToken(getString(R.string.google_signin_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<Button>(R.id.btnGoogleSignIn).setOnClickListener {
            signIn()
        }

        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            navigateToMain(null)
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            navigateToMain(account)
        }
    }

    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                navigateToMain(account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Error: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) {
        val name = account?.displayName ?: "Invitado"
        val email = account?.email
        val photoUrl = account?.photoUrl?.toString()
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("user_name", name)
            putExtra("user_email", email)
            putExtra("user_photo", photoUrl)
        }
        startActivity(intent)
        finish()
    }
}
```

**Vinculado con:** `MainActivity` (destino), `activity_login.xml` (UI), `strings.xml` (client ID y scheme OAuth), `YouTubeDataManager.refreshToken` (token de acceso).

---

### 6.18 Player — `player/ExoPlayerManager.kt`

**Qué hace:** envoltorio del ExoPlayer de media3. Clave: conecta una **fábrica de fuentes de datos propia** — `RangeFixingDataSource(OkHttpDataSource)` — que usa el mismo cliente OkHttp que InnerTube y corrige los rangos HTTP (ver 6.20). Esto fue el fix definitivo del error 403 de googlevideo.

```kotlin
package com.miappvideos.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.miappvideos.api.innertube.RotatingHttpClient

class ExoPlayerManager(context: Context) {

    private val dataSourceFactory = object : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RangeFixingDataSource(
                OkHttpDataSource.Factory(RotatingHttpClient.client()).createDataSource()
            )
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .setMediaSourceFactory(
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
        )
        .build()

    var isPlaying: Boolean
        get() = player.isPlaying
        set(value) {
            if (value) player.play() else player.pause()
        }

    var currentVideoId: String? = null
    var currentTitle: String = ""
    var currentThumbnail: String? = null

    private val listeners = mutableListOf<Player.Listener>()

    fun playUrl(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        player.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        player.removeListener(listener)
    }

    fun release() {
        player.release()
    }

    fun getAudioUrl(audioStreams: List<com.miappvideos.model.AudioStream>?): String? {
        if (audioStreams.isNullOrEmpty()) return null
        return audioStreams.maxByOrNull { it.bitrate ?: 0 }?.url
    }

    fun getVideoUrl(videoStreams: List<com.miappvideos.model.VideoStream>?): String? {
        if (videoStreams.isNullOrEmpty()) return null
        val filtered = videoStreams.filter { it.videoOnly == false }
        return filtered.maxByOrNull { it.height ?: 0 }?.url
            ?: videoStreams.maxByOrNull { it.height ?: 0 }?.url
    }
}
```

**Vinculado con:** `MainActivity` (creación + listeners), `ExoPlayerHolder`, `PlayerService`, `RangeFixingDataSource`, `RotatingHttpClient`.

---

### 6.19 Player — `player/PlayerService.kt` (incluye `ExoPlayerHolder`)

**Qué hace:** servicio de reproducción en segundo plano (`MediaSessionService`). Al arrancar crea el canal de notificación y la `MediaSession` sobre el player compartido (`ExoPlayerHolder`), y publica una notificación fija de reproducción. `ExoPlayerHolder` es un objeto global para compartir el player entre Activity y Service.

```kotlin
package com.miappvideos.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.miappvideos.MainActivity
import com.miappvideos.R

class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession.Builder(this, ExoPlayerHolder.player.player)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra("video_id")
        val title = intent?.getStringExtra("title") ?: "Reproduciendo"

        if (videoId != null) {
            ExoPlayerHolder.player.currentVideoId = videoId
            ExoPlayerHolder.player.currentTitle = title
            startForeground(NOTIFICATION_ID, createNotification(title))
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        ExoPlayerHolder.player.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de música en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación para reproducción de música en segundo plano"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Reproduciendo música en segundo plano")
            .setSmallIcon(com.miappvideos.R.drawable.ic_play_arrow)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "background_playback"
        private const val NOTIFICATION_ID = 1001
    }
}

object ExoPlayerHolder {
    lateinit var player: ExoPlayerManager
}
```

**Vinculado con:** `MainActivity.startBackgroundPlayback` (lo lanza), `AndroidManifest.xml` (servicio mediaPlayback), `ExoPlayerManager` (player compartido).

---

### 6.20 Player — `player/RangeFixingDataSource.kt` (el fix del 403)

**Qué hace:** envuelve un `HttpDataSource` y arregla dos problemas reales que descubrimos con googlevideo:
1. ExoPlayer pide el primer segmento con `Range: bytes=0-` (longitud desconocida) → googlevideo responde **403**.
2. Algunas URLs (con el parámetro `gcr`, restricción geográfica firmada) rechazan rangos mayores a ~1 MB → **403**.

Solución: convierte el rango en cerrado usando el tamaño real `clen` del URL, y limita cada petición a 1 MB máximo. ExoPlayer continúa con la siguiente petición al terminar cada rango (funciona comprobado: la canción completa se reproduce).

```kotlin
package com.miappvideos.player

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Envuelve otro HttpDataSource y corrige las peticiones con rango abierto.
 *
 * 1) ExoPlayer pide el primer segmento con `Range: bytes=0-` (longitud desconocida)
 *    y googlevideo responde 403 a los rangos abiertos, asi que usamos el tamano
 *    real del parametro `clen` (o un limite generoso si no existe).
 * 2) Algunos URLs (con restriccion geografica `gcr`) rechazan rangos mayores
 *    a ~1 MB (403), por eso cada peticion se limita a 1 MB como maximo;
 *    ExoPlayer continua con la siguiente peticion al terminar el rango.
 */
class RangeFixingDataSource(private val upstream: HttpDataSource) : HttpDataSource {

    companion object {
        private const val MAX_CHUNK = 1024L * 1024L
    }

    override fun open(dataSpec: DataSpec): Long {
        val fixed = if (dataSpec.length < 0L) {
            val clen = parseClen(dataSpec.uri)
            val available: Long = if (clen != null) (clen - dataSpec.position).coerceAtLeast(1L) else MAX_CHUNK
            dataSpec.buildUpon()
                .setLength(available.coerceAtMost(MAX_CHUNK))
                .build()
        } else {
            dataSpec.buildUpon()
                .setLength(dataSpec.length.coerceAtMost(MAX_CHUNK))
                .build()
        }
        return upstream.open(fixed)
    }

    private fun parseClen(uri: Uri): Long? {
        return try {
            uri.getQueryParameter("clen")?.toLong()
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        upstream.close()
    }

    override fun getUri(): Uri? = upstream.uri

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int =
        upstream.read(buffer, offset, readLength)

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun setRequestProperty(name: String, value: String) {
        upstream.setRequestProperty(name, value)
    }

    override fun clearRequestProperty(name: String) {
        upstream.clearRequestProperty(name)
    }

    override fun clearAllRequestProperties() {
        upstream.clearAllRequestProperties()
    }

    override fun getResponseCode(): Int = upstream.responseCode

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }
}
```

**Vinculado con:** `ExoPlayerManager` (único usuario), `OkHttpDataSource`, `RotatingHttpClient`. Nota: la interfaz `HttpDataSource` de media3 1.2.1 exige exactamente estos métodos (setRequestProperty, clearRequestProperty, clearAllRequestProperties, getResponseCode, getResponseHeaders, open, close, read, addTransferListener de DataSource).

---

### 6.21 Adaptadores — `adapter/VideoAdapter.kt`

**Qué hace:** pinta las tarjetas del feed. Formatea duración (segundos → h:mm:ss), vistas (K/M/B) y fecha relativa. Usa Coil para las miniaturas con esquinas redondeadas.

```kotlin
package com.miappvideos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.miappvideos.R
import com.miappvideos.model.PipedVideo
import java.text.SimpleDateFormat
import java.util.Locale

class VideoAdapter(
    private var videos: List<PipedVideo>,
    private val onVideoClick: (PipedVideo) -> Unit,
    private val onOptionsClick: (PipedVideo) -> Unit = {}
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    fun updateVideos(newVideos: List<PipedVideo>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video)
        holder.itemView.setOnClickListener { onVideoClick(video) }
        holder.btnOptions.setOnClickListener { onOptionsClick(video) }
    }

    override fun getItemCount() = videos.size

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnOptions: ImageView = itemView.findViewById(R.id.btnOptions)
        private val thumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val channel: TextView = itemView.findViewById(R.id.tvChannel)
        private val duration: TextView = itemView.findViewById(R.id.tvDuration)

        fun bind(video: PipedVideo) {
            title.text = video.title
            val parts = mutableListOf(video.uploaderName ?: "Desconocido")
            val viewsText = formatViews(video.views)
            if (viewsText.isNotEmpty()) parts.add(viewsText)
            val dateText = formatDate(video.uploadedDate)
            if (dateText.isNotEmpty()) parts.add(dateText)
            channel.text = parts.joinToString(" • ")

            val durationText = formatDuration(video.duration)
            if (durationText.isNotEmpty()) {
                duration.text = durationText
                duration.visibility = View.VISIBLE
            } else {
                duration.visibility = View.GONE
            }

            video.thumbnail?.let { url ->
                thumbnail.load(url) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(12f))
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            }
        }

        private fun formatDuration(seconds: Long?): String {
            if (seconds == null || seconds <= 0) return ""
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%d:%02d", m, s)
        }

        private fun formatViews(views: Long?): String {
            if (views == null) return ""
            return when {
                views >= 1_000_000_000 -> "${views / 1_000_000_000}B"
                views >= 1_000_000 -> "${views / 1_000_000}M"
                views >= 1_000 -> "${views / 1_000}K"
                else -> "$views"
            }
        }

        private fun formatDate(iso: String?): String {
            if (iso == null) return ""
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val date = parser.parse(iso) ?: return ""
                val days = (System.currentTimeMillis() - date.time) / (1000 * 60 * 60 * 24)
                when {
                    days < 1 -> "hoy"
                    days == 1L -> "ayer"
                    days < 30 -> "hace $days días"
                    days < 365 -> "hace ${days / 30} meses"
                    else -> "hace ${days / 365} años"
                }
            } catch (_: Exception) {
                ""
            }
        }
    }
}
```

**Vinculado con:** `item_video.xml`, `MainActivity` (feed + historial del perfil).

---

### 6.22 Adaptadores — `adapter/QueueAdapter.kt`

**Qué hace:** pinta la cola de reproducción y resalta el item actual con fondo rojo translúcido (`queue_current`).

```kotlin
package com.miappvideos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.miappvideos.R
import com.miappvideos.model.PipedVideo

class QueueAdapter(
    private var videos: List<PipedVideo>,
    private var currentIndex: Int = -1,
    private val onItemClick: (Int) -> Unit,
    private val onOptionsClick: (Int) -> Unit = {}
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    fun updateQueue(newVideos: List<PipedVideo>, newCurrentIndex: Int) {
        videos = newVideos
        currentIndex = newCurrentIndex
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video, position == currentIndex)
        holder.itemView.setOnClickListener { onItemClick(position) }
        holder.btnOptions.setOnClickListener { onOptionsClick(position) }
    }

    override fun getItemCount() = videos.size

    class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnOptions: ImageView = itemView.findViewById(R.id.btnQueueOptions)
        private val thumbnail: ImageView = itemView.findViewById(R.id.queueThumbnail)
        private val title: TextView = itemView.findViewById(R.id.queueTitle)
        private val channel: TextView = itemView.findViewById(R.id.queueChannel)

        fun bind(video: PipedVideo, isCurrent: Boolean) {
            title.text = video.title
            channel.text = video.uploaderName ?: "Desconocido"
            itemView.setBackgroundResource(
                if (isCurrent) R.color.queue_current
                else android.R.color.transparent
            )
            video.thumbnail?.let { url ->
                thumbnail.load(url) {
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            }
        }
    }
}
```

**Vinculado con:** `item_queue.xml`, `MainActivity.setupQueue`/`refreshQueue`.

---

### 6.23 Adaptadores — `adapter/PlaylistAdapter.kt`

**Qué hace:** lista simple de playlists (solo títulos) para el diálogo de perfil.

```kotlin
package com.miappvideos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miappvideos.R
import com.miappvideos.model.YouTubePlaylist

class PlaylistAdapter(
    private val playlists: List<YouTubePlaylist>
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.text.text = playlists[position].snippet?.title ?: "Sin título"
    }

    override fun getItemCount() = playlists.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(android.R.id.text1)
    }
}
```

**Vinculado con:** `MainActivity.openAccount` (perfil) y `YouTubeDataManager.getPlaylists`.

---

### 6.24 Layout — `res/layout/activity_main.xml`

**Qué hace:** layout raíz de la app. Contiene (de arriba a abajo): toolbar (menú, logo, crear, buscar, avatar), fila de chips de categorías, campo de búsqueda oculto, feed (SwipeRefresh + RecyclerView), contenedor del reproductor (mini-player + pantalla completa con PlayerView, barra de controles y cola) y la barra de navegación inferior.

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/rootLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:colorBackground">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="0dp"
        android:layout_height="56dp"
        android:background="?attr/colorSurface"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <ImageButton
            android:id="@+id/btnMenu"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_gravity="start|center_vertical"
            android:src="@drawable/ic_menu"
            android:padding="8dp"
            android:contentDescription="Menú"
            android:tint="?attr/colorOnSurface"
            android:background="?attr/selectableItemBackgroundBorderless" />

        <TextView
            android:id="@+id/logoTextView"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="start|center_vertical"
            android:layout_marginStart="8dp"
            android:text="@string/app_name"
            android:textColor="@color/youtube_red"
            android:textSize="20sp"
            android:textStyle="bold" />

        <ImageButton
            android:id="@+id/btnCreate"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_gravity="end"
            android:src="@drawable/ic_edit"
            android:padding="8dp"
            android:contentDescription="Crear"
            android:tint="?attr/colorOnSurface"
            android:background="?attr/selectableItemBackgroundBorderless" />

        <ImageButton
            android:id="@+id/btnSearch"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_gravity="end"
            android:layout_marginEnd="4dp"
            android:src="@drawable/ic_search"
            android:padding="8dp"
            android:contentDescription="Buscar"
            android:tint="?attr/colorOnSurface"
            android:background="?attr/selectableItemBackgroundBorderless" />

        <ImageView
            android:id="@+id/btnAvatar"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:layout_gravity="end|center_vertical"
            android:layout_marginEnd="4dp"
            android:scaleType="centerCrop"
            android:src="@drawable/ic_account_circle"
            android:contentDescription="Cuenta"
            android:background="?attr/selectableItemBackgroundBorderless" />

    </com.google.android.material.appbar.MaterialToolbar>

    <HorizontalScrollView
        android:id="@+id/chipsRow"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:scrollbars="none"
        app:layout_constraintTop_toBottomOf="@id/toolbar"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/chipGroup"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingHorizontal="8dp"
            android:paddingVertical="4dp"
            app:singleLine="true"
            app:singleSelection="true">

            <com.google.android.material.chip.Chip
                android:id="@+id/chipTodo"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Todo"
                android:checked="true" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipMusica"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Música" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipMixes"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Mixes" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipVideojuegos"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Videojuegos" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipNoticias"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Noticias" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipDeportes"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Deportes" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipComedia"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Comedia" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipEducacion"
                style="@style/Widget.MaterialComponents.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="Educación" />

        </com.google.android.material.chip.ChipGroup>

    </HorizontalScrollView>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/searchLayout"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:visibility="gone"
        android:elevation="4dp"
        android:layout_marginHorizontal="8dp"
        android:layout_marginTop="4dp"
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
        app:layout_constraintTop_toBottomOf="@id/chipsRow"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/searchInput"
            android:layout_width="match_parent"
            android:layout_height="40dp"
            android:hint="Buscar en YouTube"
            android:textColor="?attr/colorOnSurface"
            android:textColorHint="?attr/colorOnSurfaceVariant"
            android:imeOptions="actionSearch"
            android:inputType="text"
            android:maxLines="1" />

    </com.google.android.material.textfield.TextInputLayout>

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipeRefresh"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toBottomOf="@id/chipsRow"
        app:layout_constraintBottom_toTopOf="@id/playerContainer"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerVideos"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:paddingTop="4dp"
            android:paddingBottom="8dp"
            android:scrollbars="vertical"
            android:overScrollMode="never" />

    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

    <LinearLayout
        android:id="@+id/playerContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="?attr/colorSurface"
        android:elevation="8dp"
        android:visibility="gone"
        android:clickable="true"
        android:focusable="true"
        app:layout_constraintBottom_toTopOf="@id/bottomNavigation"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <!-- Mini-player compact bar -->
        <LinearLayout
            android:id="@+id/miniPlayer"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:gravity="center_vertical"
            android:paddingHorizontal="8dp"
            android:orientation="horizontal"
            android:background="?attr/colorSurface"
            android:clickable="true"
            android:focusable="true">

            <ImageView
                android:id="@+id/miniThumbnail"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:scaleType="centerCrop"
                android:contentDescription="Thumbnail" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:paddingHorizontal="8dp">
                <TextView
                    android:id="@+id/miniTitle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:maxLines="1"
                    android:ellipsize="end"
                    android:textColor="?attr/colorOnSurface"
                    android:textSize="13sp" />
                <TextView
                    android:id="@+id/miniChannel"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:maxLines="1"
                    android:ellipsize="end"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:textSize="11sp" />
            </LinearLayout>

            <ImageButton
                android:id="@+id/miniPrev"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:src="@drawable/ic_skip_previous"
                android:tint="?attr/colorOnSurface"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Anterior" />

            <ImageButton
                android:id="@+id/miniPlayPause"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:src="@drawable/ic_pause"
                android:tint="?attr/colorOnSurface"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Play/Pause" />

            <ImageButton
                android:id="@+id/miniNext"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:src="@drawable/ic_skip_next"
                android:tint="?attr/colorOnSurface"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Siguiente" />

            <ImageButton
                android:id="@+id/miniExpand"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:src="@drawable/ic_expand_less"
                android:tint="?attr/colorOnSurface"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Expandir" />

        </LinearLayout>

        <!-- Pantalla de reproducción (video arriba + cola debajo) -->
        <LinearLayout
            android:id="@+id/fullPlayerContainer"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:visibility="gone">

            <androidx.media3.ui.PlayerView
                android:id="@+id/playerView"
                android:layout_width="match_parent"
                android:layout_height="240dp"
                android:background="#000000"
                app:use_controller="true"
                app:show_buffering="when_playing"
                app:resize_mode="fit" />

            <LinearLayout
                android:id="@+id/playerControlsBar"
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:gravity="center_vertical"
                android:paddingHorizontal="8dp"
                android:orientation="horizontal"
                android:background="?attr/colorSurface">

                <ImageButton
                    android:id="@+id/btnCollapse"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:src="@drawable/ic_close"
                    android:tint="?attr/colorOnSurface"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Cerrar" />

                <TextView
                    android:id="@+id/titleTextView"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:textColor="?attr/colorOnSurface"
                    android:textSize="13sp"
                    android:maxLines="1"
                    android:ellipsize="end"
                    android:paddingHorizontal="4dp" />

                <ImageButton
                    android:id="@+id/btnShuffle"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:src="@drawable/ic_shuffle"
                    android:tint="?attr/colorOnSurfaceVariant"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Aleatorio" />

                <ImageButton
                    android:id="@+id/btnRepeat"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:src="@drawable/ic_repeat"
                    android:tint="?attr/colorOnSurfaceVariant"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Repetir" />

                <ImageButton
                    android:id="@+id/btnPlayPause"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:src="@drawable/ic_pause"
                    android:tint="?attr/colorOnSurface"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Play/Pause" />

                <ImageButton
                    android:id="@+id/btnBackground"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:src="@drawable/ic_headphones"
                    android:tint="?attr/colorOnSurface"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Reproducir en segundo plano" />

                <ImageButton
                    android:id="@+id/btnPip"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:src="@drawable/ic_picture_in_picture"
                    android:tint="?attr/colorOnSurface"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Picture in Picture" />

            </LinearLayout>

            <TextView
                android:id="@+id/queueHeader"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingHorizontal="16dp"
                android:paddingVertical="8dp"
                android:text="En cola"
                android:textColor="?attr/colorOnSurface"
                android:textSize="15sp"
                android:textStyle="bold" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/queueRecyclerView"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:scrollbars="vertical"
                android:overScrollMode="never" />

        </LinearLayout>

    </LinearLayout>

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNavigation"
        android:layout_width="0dp"
        android:layout_height="56dp"
        android:background="?attr/colorSurface"
        app:menu="@menu/bottom_nav_menu"
        app:itemIconTint="?attr/colorOnSurface"
        app:itemTextColor="?attr/colorOnSurface"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

**Vinculado con:** `MainActivity` (todos los ids), `bottom_nav_menu.xml`, `ic_*` drawables.

---

### 6.25 Layout — `res/layout/item_video.xml`

**Qué hace:** tarjeta del feed: miniatura 16:9, duración superpuesta (fondo `bg_duration`), botón ⋮, título y canal/vistas/fecha.

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?android:colorBackground">

    <androidx.cardview.widget.CardView
        android:id="@+id/cardThumbnail"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginHorizontal="0dp"
        android:layout_marginTop="0dp"
        app:cardCornerRadius="0dp"
        app:cardElevation="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintDimensionRatio="16:9">

        <ImageView
            android:id="@+id/ivThumbnail"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="centerCrop"
            android:background="@android:color/darker_gray" />

        <TextView
            android:id="@+id/tvDuration"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="6dp"
            android:paddingHorizontal="4dp"
            android:paddingVertical="1dp"
            android:background="@drawable/bg_duration"
            android:textColor="@android:color/white"
            android:textSize="11sp"
            android:textStyle="bold"
            android:visibility="gone" />

    </androidx.cardview.widget.CardView>

    <ImageButton
        android:id="@+id/btnOptions"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:src="@drawable/ic_more_vert"
        android:tint="?attr/colorOnSurface"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="Opciones"
        app:layout_constraintTop_toBottomOf="@id/cardThumbnail"
        app:layout_constraintEnd_toEndOf="parent" />

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="4dp"
        android:textSize="14sp"
        android:textColor="?attr/colorOnSurface"
        android:maxLines="2"
        android:ellipsize="end"
        android:lineSpacingExtra="2dp"
        app:layout_constraintTop_toBottomOf="@id/cardThumbnail"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toStartOf="@id/btnOptions" />

    <TextView
        android:id="@+id/tvChannel"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_marginTop="2dp"
        android:layout_marginEnd="12dp"
        android:textSize="12sp"
        android:textColor="?attr/colorOnSurfaceVariant"
        android:maxLines="1"
        android:ellipsize="end"
        app:layout_constraintTop_toBottomOf="@id/tvTitle"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        android:layout_marginBottom="12dp" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

**Vinculado con:** `VideoAdapter`.

---

### 6.26 Layout — `res/layout/item_queue.xml`

**Qué hace:** fila de la cola: miniatura 90dp, título, canal y botón ⋮.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="64dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingHorizontal="8dp"
    android:paddingVertical="6dp"
    android:background="?android:colorBackground">

    <ImageView
        android:id="@+id/queueThumbnail"
        android:layout_width="90dp"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:background="@android:color/darker_gray" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:paddingHorizontal="10dp">

        <TextView
            android:id="@+id/queueTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="2"
            android:ellipsize="end"
            android:textColor="?attr/colorOnSurface"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/queueChannel"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:maxLines="1"
            android:ellipsize="end"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:textSize="11sp" />

    </LinearLayout>

    <ImageButton
        android:id="@+id/btnQueueOptions"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:src="@drawable/ic_more_vert"
        android:tint="?attr/colorOnSurface"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="Opciones" />

</LinearLayout>
```

**Vinculado con:** `QueueAdapter`.

---

### 6.27 Layout — `res/layout/activity_login.xml`

**Qué hace:** pantalla de login: logo, eslogan, botón de Google y botón "Continuar como invitado".

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:background="?android:colorBackground"
    android:padding="32dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="MyTube"
        android:textColor="@color/youtube_red"
        android:textSize="48sp"
        android:textStyle="bold" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Tu YouTube sin anuncios"
        android:textColor="?attr/colorOnSurface"
        android:textSize="16sp"
        android:layout_marginTop="8dp" />

    <Button
        android:id="@+id/btnGoogleSignIn"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_marginTop="48dp"
        android:text="Iniciar sesión con Google"
        android:textColor="#FFFFFF"
        android:backgroundTint="#4285F4"
        android:textSize="16sp" />

    <Button
        android:id="@+id/btnSkip"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="12dp"
        android:text="Continuar como invitado"
        android:textColor="?attr/colorOnSurface"
        android:backgroundTint="@android:color/transparent"
        android:textSize="14sp" />

</LinearLayout>
```

**Vinculado con:** `LoginActivity`.

---

### 6.28 Layout — `res/layout/profile_dialog.xml`

**Qué hace:** diálogo de perfil: avatar, nombre, correo, playlists, "Vistos recientemente" y botón de cerrar sesión.

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:colorBackground">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="24dp">

            <ImageView
                android:id="@+id/profileAvatar"
                android:layout_width="72dp"
                android:layout_height="72dp"
                android:scaleType="centerCrop"
                android:src="@drawable/ic_account_circle"
                android:contentDescription="Avatar" />

            <TextView
                android:id="@+id/profileName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:textColor="?attr/colorOnSurface"
                android:textSize="20sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/profileEmail"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textColor="?attr/colorOnSurfaceVariant"
                android:textSize="14sp" />
        </LinearLayout>

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="?attr/colorOnSurfaceVariant"
            android:alpha="0.2" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="8dp"
            android:text="Tus playlists"
            android:textColor="?attr/colorOnSurface"
            android:textSize="16sp"
            android:textStyle="bold" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/profilePlaylists"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:layout_marginTop="16dp"
            android:background="?attr/colorOnSurfaceVariant"
            android:alpha="0.2" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="8dp"
            android:text="Vistos recientemente"
            android:textColor="?attr/colorOnSurface"
            android:textSize="16sp"
            android:textStyle="bold" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/profileHistory"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />

        <Button
            android:id="@+id/btnLogout"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:layout_marginTop="24dp"
            android:text="Cerrar sesión"
            android:textColor="@android:color/white"
            android:backgroundTint="@color/youtube_red" />

    </LinearLayout>

</ScrollView>
```

**Vinculado con:** `MainActivity.openAccount`.

---

### 6.29 Menú — `res/menu/bottom_nav_menu.xml`

**Qué hace:** define las 5 pestañas inferiores con sus iconos.

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_home"
        android:icon="@drawable/ic_home"
        android:title="Inicio" />
    <item
        android:id="@+id/nav_trending"
        android:icon="@drawable/ic_trending_up"
        android:title="Tendencias" />
    <item
        android:id="@+id/nav_music"
        android:icon="@drawable/ic_music_note"
        android:title="Música" />
    <item
        android:id="@+id/nav_subs"
        android:icon="@drawable/ic_subscriptions"
        android:title="Suscripciones" />
    <item
        android:id="@+id/nav_account"
        android:icon="@drawable/ic_account_circle"
        android:title="Cuenta" />
</menu>
```

**Vinculado con:** `activity_main.xml` (BottomNavigationView) y `MainActivity.setupControls`.

---

### 6.30 Valores — `res/values/strings.xml`

**Qué hace:** textos de la app + datos sensibles del proyecto de Google Cloud (client ID OAuth, scheme y API key de YouTube Data).

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">MyTube</string>
    <string name="search_hint">Buscar videos...</string>
    <string name="no_results">Sin resultados</string>
    <string name="error_loading">Error al cargar videos</string>
    <string name="channel_label">Canal:</string>
    <string name="views_label">Vistas:</string>
    <string name="google_signin_scheme" translatable="false">com.googleusercontent.apps.38145403059-lkn4onppsoqhe6sdlbpufnr9oqd2n22m</string>
    <string name="youtube_api_key" translatable="false">AIzaSyAvAFo6fXIzJto1RkejUWg-BJHKon2qZNo</string>
    <string name="google_signin_client_id" translatable="false">38145403059-lkn4onppsoqhe6sdlbpufnr9oqd2n22m.apps.googleusercontent.com</string>
</resources>
```

**Vinculado con:** `YouTubeDataManager` (API key), `LoginActivity` (client ID + scheme), `AndroidManifest.xml` (scheme del deep link OAuth).

---

### 6.31 Valores — `res/values/colors.xml`

**Qué hace:** paleta: rojo YouTube, fondo del item actual de la cola (rojo translúcido) y colores del tema oscuro.

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="youtube_red">#FF0000</color>
    <color name="queue_current">#33FF0000</color>
    <color name="dark_surface">#212121</color>
    <color name="dark_background">#121212</color>
</resources>
```

**Vinculado con:** `QueueAdapter` (`queue_current`), logos y temas.

---

### 6.32 Temas — `res/values/themes.xml` (claro) y `res/values-night/themes.xml` (oscuro)

**Qué hace:** tema Material DayNight con acento rojo. El claro usa superficies blancas; el oscuro (values-night) usa `#121212`/`#212121`. `MainActivity.cycleTheme` alterna entre ellos y se persiste en `theme_prefs`.

`values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MiAppVideos" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">#FF0000</item>
        <item name="colorPrimaryVariant">#CC0000</item>
        <item name="colorOnPrimary">#FFFFFF</item>
        <item name="colorSecondary">#FF0000</item>
        <item name="colorSecondaryVariant">#CC0000</item>
        <item name="colorOnSecondary">#FFFFFF</item>
        <item name="android:statusBarColor">#E0E0E0</item>
        <item name="android:navigationBarColor">#FFFFFF</item>
        <item name="android:windowBackground">#FFFFFF</item>
        <item name="colorSurface">#FFFFFF</item>
        <item name="colorOnSurface">#DE000000</item>
        <item name="colorOnSurfaceVariant">#99000000</item>
    </style>
</resources>
```

`values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MiAppVideos" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">#FF0000</item>
        <item name="colorPrimaryVariant">#CC0000</item>
        <item name="colorOnPrimary">#FFFFFF</item>
        <item name="colorSecondary">#212121</item>
        <item name="colorSecondaryVariant">#000000</item>
        <item name="colorOnSecondary">#FFFFFF</item>
        <item name="android:statusBarColor">#212121</item>
        <item name="android:navigationBarColor">#000000</item>
        <item name="android:windowBackground">#121212</item>
        <item name="colorSurface">#212121</item>
        <item name="colorOnSurface">#FFFFFF</item>
        <item name="colorOnSurfaceVariant">#AAAAAA</item>
    </style>
</resources>
```

**Vinculado con:** `AndroidManifest.xml` y `MainActivity.cycleTheme`/`applySavedTheme`.

---

### 6.33 Drawables — `res/drawable/bg_duration.xml`

**Qué hace:** fondo semitransparente redondeado para el sello de duración sobre las miniaturas.

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#99000000" />
    <corners android:radius="3dp" />
</shape>
```

**Vinculado con:** `item_video.xml` (tvDuration).

---

### 6.34 Drawable — `res/drawable/ic_launcher.xml`

**Qué hace:** icono de la app: fondo rojo con un triángulo de play blanco (108dp).

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF0000"
        android:pathData="M0,0h108v108H0z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M42,33l36,21l-36,21z" />
</vector>
```

**Vinculado con:** `AndroidManifest.xml` (`android:icon`).

---

### 6.35 Iconos Material — los 22 `ic_*.xml` restantes

**Qué hace:** todos son vectores Material con la misma plantilla (viewport 24x24, `fillColor` de tema según dónde se usen). Tabla de uso y path:

| Archivo | Uso | pathData |
|---|---|---|
| `ic_account_circle` | Avatar/cuenta | `M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,5c1.66,0 3,1.34 3,3s-1.34,3 -3,3 -3,-1.34 -3,-3 1.34,-3 3,-3zM12,19.2c-2.5,0 -4.71,-1.28 -6,-3.22 0.03,-1.99 4,-3.08 6,-3.08 1.99,0 5.97,1.09 6,3.08 -1.29,1.94 -3.5,3.22 -6,3.22z` |
| `ic_close` | Cerrar (colapsar player) | `M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z` |
| `ic_dark_mode` | (tema oscuro, reservado) | `M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4 -0.44,-0.06 -0.9,-0.1 -1.36,-0.1z` |
| `ic_edit` | Botón crear | `M3,17.25L3,21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z` |
| `ic_expand_less` | Expandir mini-player | `M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z` |
| `ic_expand_more` | (reservado) | `M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z` |
| `ic_headphones` | Reproducir en 2º plano | `M12,3a9,9 0 0,0 -9,9v7a3,3 0 0,0 3,3h3v-8L5,14v-2a7,7 0 0,1 14,0v2h-4v8h3a3,3 0 0,0 3,-3v-7a9,9 0 0,0 -9,-9z` |
| `ic_home` | Pestaña Inicio | `M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z` |
| `ic_light_mode` | (tema claro, reservado) | `M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM18,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1L13,2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41l-1.06,-1.06zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06z` |
| `ic_menu` | Menú ☰ | `M3,18h18v-2L3,16v2zM3,13h18v-2L3,11v2zM3,6v2h18L21,6L3,6z` |
| `ic_more_vert` | Opciones ⋮ (feed y cola) | `M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z` |
| `ic_music_note` | Pestaña Música | `M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z` |
| `ic_pause` | Pausa | `M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z` |
| `ic_picture_in_picture` | PiP | `M19,11h-8v6h8v-6zM21,3L3,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,19.01L3,19.01L3,5h18v14.01z` |
| `ic_play_arrow` | Play + icono de notificación | `M8,5v14l11,-7z` |
| `ic_repeat` | Repetir | `M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z` |
| `ic_search` | Buscar | `M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z` |
| `ic_settings_brightness` | (tema, reservado) | `M21,3L3,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM19,19L5,19L5,5h14v14zM15,7.5L15,5h-2.5L12,3.5 10.5,5L8,5v2.5L5.5,9 8,11.5L8,14l2.5,0L12,16.5 13.5,14L16,14v-2.5L18.5,9 16,7.5zM12,9c1.66,0 3,1.34 3,3s-1.34,3 -3,3L12,9z` |
| `ic_shuffle` | Aleatorio | `M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5L20,4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20L20,20v-5.5l-2.04,2.04 -3.13,-3.13z` |
| `ic_skip_next` | Siguiente | `M6,18l8.5,-6L6,6v12zM16,6v12h2L18,6h-2z` |
| `ic_skip_previous` | Anterior | `M6,6h2v12L6,18zM9.5,12l8.5,6L18,6z` |
| `ic_subscriptions` | Pestaña Suscripciones | `M20,8L4,8L4,6h16v2zM18,2L6,2v2h12L18,2zM22,10L2,10v10h20L22,10zM12,13l4,2.5 -4,2.5v-5z` |
| `ic_trending_up` | Pestaña Tendencias | `M16,6l2.29,2.29 -4.88,4.88 -4,-4L2,16.59 3.41,18l6,-6 4,4 6.3,-6.29L22,12V6z` |

---

## 7. Datos de configuración externa

| Dato | Valor | Dónde |
|---|---|---|
| Proyecto Google Cloud | `prefab-isotope-504100-n4` | Consola GCP |
| YouTube Data API v3 | Habilitada | Consola GCP |
| API key (Data API) | `AIzaSyAvAFo6fXIzJto1RkejUWg-BJHKon2qZNo` | `strings.xml` |
| Cliente OAuth web | `38145403059-lkn4onppsoqhe6sdlbpufnr9oqd2n22m` | `strings.xml` |
| Scheme OAuth | `com.googleusercontent.apps.38145403059-...n22m` | `strings.xml` + manifest |
| SHA-1 firma | `93:9A:8C:BA:DE:AB:09:7A:17:F4:F9:1E:3F:2C:7D:C8:C5:9B:45:E3` | Consola GCP |
| Scope | `https://www.googleapis.com/auth/youtube.readonly` | LoginActivity |
| API key InnerTube | `AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8` | ClientProfiles |
| Perfil activo | IOS `21.03.1` (www.youtube.com) | ClientProfiles |
| Instancia Piped | `https://pipedapi.kavin.rocks/` (caída) | PipedApi |

---

## 8. Mantenimiento importante

1. **ClientVersions InnerTube**: cuando las canciones dejen de reproducirse y en logcat se vea `Playability para ...: UNPLAYABLE/LOGIN_REQUIRED` en los 4 perfiles, hay que actualizar `ClientProfiles.kt` con versiones nuevas (se obtienen de foros de NewPipe/Piped o con HTTP Toolkit capturando el tráfico de la app oficial).
2. **403 de googlevideo**: si vuelve a aparecer, el `RangeFixingDataSource` ya lo mitiga (rangos ≤1 MB y cerrados). No bajar el `MAX_CHUNK` por debajo de 1 MB sin probar.
3. **Piped API**: si se quiere reactivar, cambiar `BASE_URL` en `PipedApi.kt` a una instancia pública funcional (ver lista de instancias en https://github.com/TeamPiped/Piped).
4. **Cuota de YouTube Data API**: el feed usa la cuota gratuita (10.000 unidades/día). Cada búsqueda cuesta 100; el feed de suscripciones consume varias (channels + playlistItems por canal).
5. **Acentos mojibake**: los textos con caracteres raros (ej. `estÃ¡`) en `MainActivity.kt` deben corregirse re-escribiendo el archivo en UTF-8.
6. **APK de release**: `gradlew assembleRelease` (sin minify). Requiere `proguard-rules.pro` si se activa `isMinifyEnabled`.
