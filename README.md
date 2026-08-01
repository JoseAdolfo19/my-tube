# MyTube

Cliente Android de YouTube orientado a música. Reproducción continua (cola infinita), video visible, búsqueda sin cuotas de API, mini player, segundo plano y picture-in-picture.

<p>
  <a href="https://mytubemusic.vercel.app/"><img src="https://img.shields.io/badge/site-mytubemusic.vercel.app-FF3D5E" alt="Sitio"></a>
  <a href="https://github.com/JoseAdolfo19/my-tube/releases/latest"><img src="https://img.shields.io/github/v/release/JoseAdolfo19/my-tube" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="Licencia"></a>
</p>

**Descarga el APK:** [Último release](https://github.com/JoseAdolfo19/my-tube/releases/latest/download/my-tube.apk) · Android 7.0+ (API 24+)

---

## Características destacadas

- **Catálogo solo música** — el feed, los filtros de género y las búsquedas están limitados a la categoría Música de YouTube.
- **Cola infinita** — al reproducir una canción se cargan 15 similares; cuando quedan 5 se agregan 10 más. Nunca repite.
- **Video visible** — ExoPlayer renderiza la pista de video (muxed itag 18 o video-only ≤720p) combinada con el audio mediante `MergingMediaSource`.
- **Sin dependencia de cuotas** — la búsqueda, el trending y los streams se resuelven directamente con el protocolo **InnerTube** de YouTube (el mismo mecanismo que usan OpenTune/Innertune): no se necesita cuota de Google Cloud para la reproducción principal.
- **Segundo plano** — `PlayerService` en foreground con notificación de MediaSession; mini player y PiP.
- **Búsqueda con historial persistente** — pantalla propia de búsqueda con consultas recientes (borrar, re-ejecutar, re-reproducir).
- **Inicio de sesión opcional con Google** — suscripciones y playlists vía YouTube Data API v3 (requiere tu propia key).
- **Tema claro/oscuro** — tarjetas estilo YouTube con duración, vistas y fecha relativa.

## Capturas

| Feed | Reproductor + cola | Mini player | Búsqueda |
|---|---|---|---|
| ![Feed](site/img/ss_feed.png) | ![Reproductor](site/img/ss_player.png) | ![Mini](site/img/ss_mini.png) | ![Búsqueda](site/img/ss_search.png) |

## Cómo se resuelven los streams (sin el corte de ~1 minuto)

Los clientes antiguos (IOS, WEB_MUSIC) responden HTTP 400 (`Precondition check failed`) o `LOGIN_REQUIRED`. La app prueba la siguiente cascada y se detiene en el primer éxito (ver `StreamResolver.resolveStreamUrl`):

1. `ANDROID` (MOBILE) — funciona; puede requerir visitorData firmado.
2. `ANDROID_VR` — **preferido**: devuelve streams completos sin login; se usa tanto para audio como para video.
3. `ANDROID_MUSIC` / `IOS_MUSIC` — se saltan en tiempo de ejecución cuando YouTube devuelve `LOGIN_REQUIRED`.
4. Fallbacks: proxy de audio local → `StreamProvider` (NewPipeExtractor / Piped).

Cada petición de cliente se firma con `visitorData` propio de la app (descargado de `https://www.youtube.com/sw.js_data`, con URL-decode) y `po_token` opcional (ver `PoTokenGenerator`). Los clientes verificados se cachean por `videoId`; los que fallan entran en un cooldown por video (`markStreamClientFailed`). Las URLs de stream se cachean en memoria hasta `expiresInSeconds`.

Las versiones de perfil de cliente y la API key de InnerTube se actualizan en:
`app/src/main/java/com/miappvideos/api/innertube/YouTubeClient.kt` y `NativeStreamExtractor.kt`.

## Búsqueda sin cuotas

- `InnerTubeSearch.searchVideos()` consulta el cliente **WEB** de YouTube (`twoColumnSearchResultsRenderer` → `videoRenderer`), ~19 resultados por consulta.
- Fallbacks: `YouTubeDataManager` (Data API v3, requiere tu key en `local.properties`) → Piped API.

Se usa desde `MainActivity` (trending + autoplay de "similares") y `SearchActivity`.

## Arquitectura

```
app/src/main/java/com/miappvideos/
├── MainActivity.kt              # Feed, chips de género, UI del reproductor, cola, autoplay
├── SearchActivity.kt            # Pantalla de búsqueda con historial persistente
├── MiAppVideosApplication.kt    # Init de la app (NewPipeExtractor)
├── api/
│   ├── MusicStreamProvider.kt   # Orquesta audio+video: InnerTube → proxy → StreamProvider
│   ├── StreamProvider.kt        # Cache de streams (NewPipeExtractor)
│   ├── YouTubeDataManager.kt    # YouTube Data API v3 (key opcional)
│   ├── YouTubeApi.kt, PipedApi.kt
│   └── innertube/
│       ├── InnerTubeClient.kt   # POST /player, /search, fetch de visitorData, retries
│       ├── StreamResolver.kt    # Cascada de clientes, selección audio+video, cache
│       ├── InnerTubeSearch.kt   # Parseo del search WEB (videoRenderer)
│       ├── YouTubeClient.kt     # Perfiles de cliente + API key de InnerTube
│       ├── PoTokenGenerator.kt  # Síntesis de po_token (token sintético)
│       └── RotatingHttpClient.kt# OkHttp compartido
├── player/
│   ├── ExoPlayerManager.kt      # Wrapper de ExoPlayer; playAudioVideo() usa MergingMediaSource
│   ├── RangeFixingDataSource.kt # Fix de rangos máx 1 MB para URLs de googlevideo
│   ├── PlayerService.kt         # Segundo plano en foreground + MediaSession
└── auth/LoginActivity.kt        # Inicio de sesión opcional con Google
```

### Flujo de reproducción

```
Tocar un video
  → MusicStreamProvider.getStream(videoId)
      → StreamResolver.resolveStreamUrl(videoId)
          → para cada cliente (ANDROID_VR, ANDROID, ANDROID_MUSIC, IOS_MUSIC):
              POST /player (InnerTube, visitorData firmado [+po_token])
              → parsea adaptiveFormats (audio, mayor bitrate) + video (muxed 18 o video-only ≤720p)
              → valida la URL (estado HTTP), cachea, devuelve ResolvedStream(audioUrl, videoUrl, cliente)
  → ExoPlayerManager.playAudioVideo(audioUrl, videoUrl)
      → MergingMediaSource(audio, video) vía OkHttpDataSource + RangeFixingDataSource
  → Cola: 15 similares precargados; +10 más cuando quedan 5 (InnerTubeSearch)
```

## Compilación

Requisitos: JDK 17+, Android SDK 34.

```bash
# 1. Clona y abre en Android Studio (o CLI):
./gradlew assembleDebug

# 2. (Opcional) Añade tus propias keys en local.properties — NO se commitean:
youtubeApiKey=AIza...            # YouTube Data API v3 (opcional: fallback de búsqueda/suscripciones)
googleSignInClientId=....apps.googleusercontent.com   # (opcional) inicio de sesión con Google

# 3. Instala:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **No se requiere key de Google Cloud** para reproducir ni buscar: la resolución InnerTube y la búsqueda WEB funcionan sin ella. La key de Data API solo alimenta suscripciones/playlists y los fallbacks.

### Build de release firmado

```bash
# Genera un keystore una sola vez (guárdalo — NO está en el repo):
keytool -genkeypair -v -keystore app/keystore/release.jks -alias mytube \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=MyTube"

# Configura las credenciales en key.properties (gitignored) o variables de entorno:
keystorePath=app/keystore/release.jks
keystorePassword=...
keyAlias=mytube
keyPassword=...
# (fallback por entorno: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

./gradlew assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

## Depuración

- `StreamResolver` loguea la cascada por video: `cliente= status= reason= pot=` y la resolución ganadora `resuelto videoId= cliente= bitrate=` (+ `video=true` cuando se resolvió URL de video).
- `InnerTubeClient` loguea el endpoint → código HTTP + primeros 80 bytes del body (p. ej. `LOGIN_REQUIRED`, `Precondition check failed`, `UNPLAYABLE`).
- Validación en dispositivo: `adb logcat -s StreamResolver:* MusicStreamProvider:*`.

## Limitaciones conocidas

- Las versiones de cliente InnerTube caducan; cuando aparezca `Precondition check failed`/`UNPLAYABLE`, actualiza el mapa de versiones en `YouTubeClient.kt`.
- Algunos videos están bloqueados por región (`UNPLAYABLE`) — decide YouTube según video/IP.
- El `po_token` sintético **no** pasa en `WEB_REMIX`; los clientes móviles (ANDROID_VR/ANDROID) funcionan sin él hoy.
- La cola es en memoria (se pierde al reiniciar la app); solo el historial de búsqueda/reproducción es persistente.

## Licencia

[GPL-3.0](LICENSE). No afiliado con Google ni YouTube.
