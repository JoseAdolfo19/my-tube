# MyTube

Aplicación Android de reproducción continua de música (todo tipo de géneros) usando YouTube como fuente.

## Características

- **Solo música**: el feed, los filtros de género y las búsquedas están limitados a la categoría *Música* de YouTube (categoría 10), excluyendo juegos, noticias, comedias, etc.
- **Filtros de géneros** en la parte superior: Pop, Reggaetón, Salsa, Cumbia, Rock, Bachata, Baladas, Vallenato, Electrónica, Corridos, entre otros.
- **Reproducción continua con cola infinita**: al entrar a un video se cargan automáticamente **15 videos similares** (solo música); cuando quedan **5 videos por reproducir** se agregan **10 más**, en bucle infinito sin repetir los ya escuchados.
- **Video visible**: el reproductor muestra el video (mp4 ≤720p) con ExoPlayer, con botones de anterior/siguiente, shuffle, repeat, PiP y modo segundo plano.
- **Reproducción en segundo plano**: al salir de la app el audio continúa (PlayerService en foreground con notificación de control). Al volver, el reproductor queda minimizado (mini player).
- **Mini player**: al hacer BACK durante la reproducción se vuelve al inicio con el video en miniatura, sin detener la música.
- **Búsqueda con historial**: pantalla de búsqueda propia con historial persistente (20 queries), borrado individual o total, y re-ejecución al tocar una búsqueda anterior.
- **Cola**: tocar cualquier ítem de la cola reproduce al instante; swipe lateral también cambia de video.
- **Historial de reproducción** persistente y categoría preferida guardada.
- **Inicio de sesión opcional** con Google (suscripciones, playlists).
- Tema claro/oscuro, tarjetas estilo YouTube con duración, vistas y fecha.

## Arquitectura

- **Kotlin + AndroidX (Media3/ExoPlayer, Material Components, Coil, Retrofit)**
- `MainActivity`: feed, chips de géneros, reproductor expandido + mini player, cola y lógica de autoplay/extensión de cola.
- `SearchActivity`: búsqueda con historial persistente; devuelve el video seleccionado a `MainActivity`.
- `player/PlayerService` + `MyMediaNotificationProvider`: reproducción en segundo plano con MediaSession y notificación de transporte.
- `api/YouTubeDataManager` (`YouTubeApi`): YouTube Data API v3 — búsquedas con `videoCategoryId=10` y `musicOnly` para similares/feed.
- `api/MusicStreamProvider`: obtiene el stream vía el proxy local; `player/RangeFixingDataSource` trocea rangos de 1 MB.
- `tools/audio_proxy.py`: proxy Python local que resuelve la URL del stream con `po_token` (yt-dlp) y hace passthrough de rangos HTTP, permitiendo reproducir el video completo (sin él, googlevideo corta el audio a ~1:05).

## Reproducción sin cortes (proxy local)

El proxy evita el límite de 1 MB de googlevideo para URLs sin `po_token`:

```bash
python tools/audio_proxy.py 8080
```

Desde el teléfono (misma red WiFi), la app usa `http://<IP_PC>:8080/audio?v=<videoId>`, que resuelve
`best[height<=720][ext=mp4]/bestaudio` con yt-dlp y reenvía los rangos completos.

Con adb (desarrollo):

```bash
adb reverse tcp:8080 tcp:8080
```

## Configuración

1. Agrega tu API key de YouTube Data API v3 en `local.properties`:
   ```
   youtubeApiKey=AIza...
   googleSignInClientId=...
   ```
2. Compila e instala:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Levanta el proxy local y comparte la IP del PC con la app (ver `MusicStreamProvider`).

## Requisitos

- Android 7.0+ (API 24+), target SDK 34
- Python 3.10+ con yt-dlp instalado (`pip install yt-dlp`) para el proxy
- YouTube Data API v3 habilitada en Google Cloud Console
