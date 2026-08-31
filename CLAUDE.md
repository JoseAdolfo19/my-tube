# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Android Build
- Build Debug APK: `./gradlew assembleDebug`
- Build Release APK: `./gradlew assembleRelease`
- Install Debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Debugging
- Stream resolution logs: `adb logcat -s StreamResolver:* MusicStreamProvider:*`
- InnerTube API logs: `adb logcat -s InnerTubeClient:*`

### Configuration
- API keys and client IDs are managed in `local.properties` (not committed).
  - `youtubeApiKey`: YouTube Data API v3
  - `googleSignInClientId`: Google OAuth Client ID
  - `proxyKey`: Authentication key for the audio proxy
  - `proxyPublicUrl`: Public endpoint for the audio proxy

## Architecture Overview

MyTube is an Android music client for YouTube that prioritizes quota-free playback and a music-centric experience.

### High-Level Structure
- **UI Layer**:
    - `MainActivity`: Manages the main feed, genre chips, and the integrated player/queue UI.
    - `SearchActivity`: Handles searching and persistent search history.
    - `LoginActivity`: Optional Google OAuth flow.
- **API & Data Layer (`com.miappvideos.api`)**:
    - **InnerTube Integration**: Uses the InnerTube protocol (via `InnerTubeClient`) to fetch search results and stream URLs without relying on the official Data API quotas.
    - **Stream Resolution**: `StreamResolver` implements a client cascade (e.g., `ANDROID_VR` $\rightarrow$ `ANDROID`) to find playable stream URLs that avoid `LOGIN_REQUIRED` or `Precondition check failed` errors.
- **Utility Layer (`com.miappvideos.util`)**:
    - **Recommendation Engine**: `RecommendationEngine` centralizes business logic for the "infinite queue", handling local ranking, music-only filtering, and artist anti-repetition to avoid consecutive tracks by the same author.
- **Playback Layer (`com.miappvideos.player`)**:
    - **ExoPlayer Integration**: `ExoPlayerManager` uses `MergingMediaSource` to combine separate audio and video streams.
    - **Foreground Service**: `PlayerService` maintains playback in the background with a `MediaSession` and notification.
    - **Data Handling**: `RangeFixingDataSource` handles specific YouTube stream range requirements.

### Key Logic Flows
- **Playback Flow**: User touch $\rightarrow$ `MusicStreamProvider` $\rightarrow$ `StreamResolver` (Cascade probe) $\rightarrow$ `ExoPlayerManager` (Merge audio+video) $\rightarrow$ Playback.
- **Infinite Queue**: When songs run low, `InnerTubeSearch` fetches similar videos, which are then filtered and re-ranked by `RecommendationEngine`.
