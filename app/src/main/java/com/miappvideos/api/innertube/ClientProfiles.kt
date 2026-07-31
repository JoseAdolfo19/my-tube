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
