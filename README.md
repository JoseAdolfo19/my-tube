# MyTube

Music-first Android client for YouTube. Continuous playback (infinite queue), visible video, search without API quotas, mini player, background playback and picture-in-picture.

<p>
  <a href="https://mytubemusic.vercel.app/"><img src="https://img.shields.io/badge/site-mytubemusic.vercel.app-FF3D5E" alt="Site"></a>
  <a href="https://github.com/JoseAdolfo19/my-tube/releases/latest"><img src="https://img.shields.io/github/v/release/JoseAdolfo19/my-tube" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License"></a>
</p>

**Download the APK:** [Latest release](https://github.com/JoseAdolfo19/my-tube/releases/latest/download/my-tube.apk) · Android 7.0+ (API 24+)

---

## Highlights

- **Music-only catalog** — feed, genre filters and search restricted to YouTube's Music category.
- **Infinite queue** — playing a track loads 15 similar videos; when 5 remain, 10 more are appended. Never repeats.
- **Visible video** — ExoPlayer renders the video track (muxed itag 18 or low-res video-only ≤720p) merged with the audio stream via `MergingMediaSource`.
- **No API quota dependency** — search, trending and streams are resolved directly through YouTube's **InnerTube** protocol (same mechanism OpenTune/Innertune use), so no Google Cloud quota is required for core playback.
- **Background playback** — foreground `PlayerService` with MediaSession notification; mini player + PiP support.
- **Search with persistent history** — own search screen with recent queries (clear, re-run, re-play).
- **Optional Google sign-in** — subscriptions and playlists via YouTube Data API v3 (requires your own key).
- **Light/dark theme** — YouTube-style cards with duration, views and relative date.

## Screenshots

| Feed | Player + queue | Mini player | Search |
|---|---|---|---|
| ![Feed](site/img/ss_feed.png) | ![Player](site/img/ss_player.png) | ![Mini](site/img/ss_mini.png) | ![Search](site/img/ss_search.png) |

## How streams are resolved (no more ~1-minute cutoff)

Legacy clients (IOS, WEB_MUSIC) either return HTTP 400 (`Precondition check failed`) or `LOGIN_REQUIRED`. The app tries the following cascade and stops at the first success (see `StreamResolver.resolveStreamUrl`):

1. `ANDROID` (MOBILE) — works, may require signed visitorData.
2. `ANDROID_VR` — **preferred**: returns full streams without login; used for both audio and video.
3. `ANDROID_MUSIC` / `IOS_MUSIC` — skipped at runtime when YouTube returns `LOGIN_REQUIRED`.
4. Fallbacks: local audio proxy → `StreamProvider` (NewPipeExtractor / Piped).

Each client request is signed with the app's own `visitorData` (fetched from `https://www.youtube.com/sw.js_data`, URL-decoded) and optional `po_token` (see `PoTokenGenerator`). Verified clients are cached per `videoId`; failing clients get a per-video cool-down (`markStreamClientFailed`). Stream URLs are cached in memory until `expiresInSeconds`.

Client profile (`ClientProfiles`/`YouTubeClient`) and API key updates are tracked in:
`app/src/main/java/com/miappvideos/api/innertube/YouTubeClient.kt` and `NativeStreamExtractor.kt`.

## Search without quotas

- `InnerTubeSearch.searchVideos()` queries YouTube's **WEB** client (`twoColumnSearchResultsRenderer` → `videoRenderer`), ~19 results per query.
- Fallbacks: `YouTubeDataManager` (Data API v3, requires your key in `local.properties`) → Piped API.

Used by `MainActivity` (trending + autoplay "similar videos") and `SearchActivity`.

## Architecture

```
app/src/main/java/com/miappvideos/
├── MainActivity.kt              # Feed, genre chips, player UI, queue, autoplay logic
├── SearchActivity.kt            # Search screen with persistent history
├── MiAppVideosApplication.kt    # App init (NewPipeExtractor)
├── api/
│   ├── MusicStreamProvider.kt   # Orchestrates audio+video: InnerTube → proxy → StreamProvider
│   ├── StreamProvider.kt        # Stream cache (NewPipeExtractor)
│   ├── YouTubeDataManager.kt    # YouTube Data API v3 (optional key)
│   ├── YouTubeApi.kt, PipedApi.kt
│   └── innertube/
│       ├── InnerTubeClient.kt   # POST /player, /search, visitorData fetch, retry logic
│       ├── StreamResolver.kt    # Client cascade, audio+video format selection, caching
│       ├── InnerTubeSearch.kt   # WEB search parsing (videoRenderer)
│       ├── YouTubeClient.kt     # Client profiles + InnerTube API key
│       ├── PoTokenGenerator.kt  # po_token synthesis (synthetic token)
│       └── RotatingHttpClient.kt# Shared OkHttp
├── player/
│   ├── ExoPlayerManager.kt      # ExoPlayer wrapper; playAudioVideo() uses MergingMediaSource
│   ├── RangeFixingDataSource.kt # 1 MB max range fix for googlevideo URLs
│   ├── PlayerService.kt         # Foreground background playback + MediaSession
└── auth/LoginActivity.kt        # Optional Google sign-in
```

### Playback flow

```
Tap video
  → MusicStreamProvider.getStream(videoId)
      → StreamResolver.resolveStreamUrl(videoId)
          → for each client (ANDROID_VR, ANDROID, ANDROID_MUSIC, IOS_MUSIC):
              POST /player (InnerTube, signed visitorData [+po_token])
              → parse adaptiveFormats (audio, max bitrate) + video (muxed 18 or ≤720p video-only)
              → validate URL (HTTP status), cache, return ResolvedStream(audioUrl, videoUrl, client)
  → ExoPlayerManager.playAudioVideo(audioUrl, videoUrl)
      → MergingMediaSource(audio, video) via OkHttpDataSource + RangeFixingDataSource
  → Queue: 15 similar videos preloaded; +10 more when 5 remain (InnerTubeSearch)
```

## Building

Requirements: JDK 17+, Android SDK 34.

```bash
# 1. Clone and open in Android Studio (or CLI):
./gradlew assembleDebug

# 2. (Optional) Add your own keys to local.properties — NOT committed:
youtubeApiKey=AIza...            # YouTube Data API v3 (optional: search/subs fallback)
googleSignInClientId=....apps.googleusercontent.com   # (optional) Google sign-in

# 3. Install:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **No Google Cloud key is required** for playback/search: InnerTube resolution and WEB search work without it. The Data API key only powers subscriptions/playlists and API fallbacks.

### Signed release builds

```bash
# Generate a keystore once (keep it safe — it is NOT in the repo):
keytool -genkeypair -v -keystore app/keystore/release.jks -alias mytube \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=MyTube"

# Configure credentials in key.properties (gitignored) or env vars:
keystorePath=app/keystore/release.jks
keystorePassword=...
keyAlias=mytube
keyPassword=...
# (env fallback: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

./gradlew assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

## Debugging

- `StreamResolver` logs the cascade per video: `cliente= status= reason= pot=` and the winning resolution `resuelto videoId= cliente= bitrate=` (+ `video=true` when a video URL was resolved).
- `InnerTubeClient` logs endpoint → HTTP code + first 80 bytes of the response body (e.g. `LOGIN_REQUIRED`, `Precondition check failed`, `UNPLAYABLE`).
- Client validation on device: `adb logcat -s StreamResolver:* MusicStreamProvider:*`.

## Known limitations

- InnerTube client versions expire over time; when `Precondition check failed`/`UNPLAYABLE` appears, bump the version map in `YouTubeClient.kt`.
- Some videos are region-blocked (`UNPLAYABLE`) — YouTube decides per video/IP.
- The synthetic `po_token` does **not** pass `WEB_REMIX`; mobile clients (ANDROID_VR/ANDROID) work without it today.
- Queue is in-memory (lost on app restart); only watch/search history persists.

## License

[GPL-3.0](LICENSE). Not affiliated with Google or YouTube.
