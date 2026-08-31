package com.miappvideos.api.innertube

import com.miappvideos.api.innertube.RotatingHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * Cliente HTTP crudo hacia los endpoints InnerTube de YouTube Music.
 * Portado de OpenTune (https://github.com/Arturo254/OpenTune) - GPL-3.0
 */
class InnerTubeClient {

    private val client = RotatingHttpClient.client()

    @Volatile
    var visitorData: String? = null
        private set

    fun setVisitorData(value: String?) {
        visitorData = value
    }

    @Volatile
    var poTokenPlayer: String? = null
        private set

    @Volatile
    var webClientPoTokenEnabled: Boolean = true
        private set

    fun setWebClientPoTokenEnabled(value: Boolean) {
        webClientPoTokenEnabled = value
    }

    fun setPoTokenPlayer(value: String?) {
        poTokenPlayer = value
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 2,
        initialDelay: Long = 300L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                val message = e.message.orEmpty()
                if (message.contains("HTTP 4")) break
                if (attempt == maxAttempts) break
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        throw lastError ?: IOException("retry fallido")
    }

    /**
     * POST /player con el body de OpenTune (context, playbackContext, serviceIntegrityDimensions).
     */
    suspend fun player(
        client_: YouTubeClient,
        videoId: String,
        signatureTimestamp: Int?,
        poToken: String?,
    ): JSONObject = withRetry {
        val context = JSONObject().put(
            "client", JSONObject()
                .put("clientName", client_.clientName)
                .put("clientVersion", client_.clientVersion)
                .put("hl", "en")
                .put("gl", "US")
                .apply {
                    client_.osName?.let { put("osName", it) }
                    client_.osVersion?.let { put("osVersion", it) }
                    client_.deviceMake?.let { put("deviceMake", it) }
                    client_.deviceModel?.let { put("deviceModel", it) }
                    client_.androidSdkVersion?.let { put("androidSdkVersion", it) }
                    client_.buildId?.let { put("buildId", it) }
                    visitorData?.let { put("visitorData", it) }
                }
        )
        val body = JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .apply {
                if (client_.useSignatureTimestamp && signatureTimestamp != null) {
                    put(
                        "playbackContext", JSONObject().put(
                            "contentPlaybackContext", JSONObject().put("signatureTimestamp", signatureTimestamp)
                        )
                    )
                }
                poToken?.let {
                    put("serviceIntegrityDimensions", JSONObject().put("poToken", it))
                }
            }
        post("player", client_, body)
    }

    /**
     * POST /search con el cliente WEB_REMIX.
     */
    suspend fun search(
        query: String,
        params: String? = null,
    ): JSONObject = withRetry {
        val context = JSONObject().put(
            "client", JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", "1.20260114.01.00")
                .put("hl", "en")
                .put("gl", "US")
                .apply {
                    visitorData?.let { put("visitorData", it) }
                }
        )
        val body = JSONObject()
            .put("context", context)
            .put("query", query)
            .apply {
                params?.let { put("params", it) }
            }
        post("search", YouTubeClient.WEB_REMIX, body)
    }

    /**
     * POST /search de YouTube (no Music) con client WEB: devuelve videoRenderer
     * con canciones individuales (el search de Music sin login solo da playlists).
     */
    suspend fun searchVideos(query: String): JSONObject = withRetry {
        val context = JSONObject().put(
            "client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", "2.20260114.00.00")
                .put("hl", "es")
                .put("gl", "PE")
                .apply {
                    visitorData?.let { put("visitorData", it) }
                }
        )
        val body = JSONObject()
            .put("context", context)
            .put("query", query)
        postToHost("https://www.youtube.com/youtubei/v1/search", YouTubeClient.WEB, body)
    }

    /**
     * Obtiene visitorData sin login desde sw.js_data (como YouTube.visitorData() de OpenTune).
     * Soporta el formato JSON {"VISITOR_DATA": [...]} y el formato array nativo
     * [[["yt.sw.adr", ...]]] con el visitorData embebido y URL-encoded.
     */
    suspend fun fetchVisitorData(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/sw.js_data")
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                .build()
            val response = client.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) return@withContext null
                val text = it.body?.string()?.removePrefix(")]}'") ?: return@withContext null
                val decoded = java.net.URLDecoder.decode(text, "UTF-8")
                val fromJson = runCatching {
                    val root = JSONObject(decoded)
                    val arr = root.optJSONArray("VISITOR_DATA")
                    var candidate: String? = null
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val v = arr.optString(i)
                            if (v.startsWith("Cgt") || v.startsWith("Cg")) {
                                candidate = v
                                break
                            }
                        }
                    }
                    candidate
                }.getOrNull()
                if (!fromJson.isNullOrBlank()) {
                    visitorData = fromJson
                    return@withContext fromJson
                }
                val fromArray = runCatching {
                    val arr = JSONArray(decoded)
                    fun find(node: Any?): String? = when (node) {
                        is JSONArray -> {
                            for (i in 0 until node.length()) {
                                find(node.opt(i))?.let { return it }
                            }
                            null
                        }
                        is String -> node.takeIf {
                            it.length > 30 && (it.startsWith("Cgt") || it.startsWith("Cg"))
                        }
                        else -> null
                    }
                    find(arr)
                }.getOrNull()
                if (!fromArray.isNullOrBlank()) visitorData = fromArray
                fromArray
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun post(endpoint: String, client_: YouTubeClient, body: JSONObject): JSONObject =
        postToHost("${YouTubeClient.API_URL_YOUTUBE_MUSIC}$endpoint", client_, body)

    private suspend fun postToHost(url: String, client_: YouTubeClient, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val fullUrl = "$url?key=${YouTubeClient.INNERTUBE_API_KEY}&prettyPrint=false"
            val isYoutubeHost = url.startsWith("https://www.youtube.com")
            val requestOrigin = if (isYoutubeHost) {
                YouTubeClient.ORIGIN_YOUTUBE
            } else {
                client_.requestOrigin()
            }
            val requestReferer = if (isYoutubeHost) {
                "${YouTubeClient.ORIGIN_YOUTUBE}/"
            } else {
                client_.requestReferer()
            }
            val requestBuilder = Request.Builder()
                .url(fullUrl)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", client_.clientId)
                .header("X-YouTube-Client-Version", client_.clientVersion)
                .header("X-Origin", requestOrigin)
                .header("Referer", requestReferer)
                .header("User-Agent", client_.userAgent)
                .apply {
                    visitorData?.let { header("X-Goog-Visitor-Id", it) }
                }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    android.util.Log.d(
                        "InnerTube",
                        "$url ${client_.clientName} -> HTTP ${response.code} (${responseBody.take(150)})"
                    )
                    throw IOException(
                        "InnerTube $url ${response.code} (${client_.clientName}): ${responseBody.take(300)}"
                    )
                }
                android.util.Log.d(
                    "InnerTube",
                    "$url ${client_.clientName} -> HTTP ${response.code} (${responseBody.take(80)})"
                )
                JSONObject(responseBody)
            }
        }
}
