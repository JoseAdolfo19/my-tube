package com.miappvideos.api.innertube

import com.miappvideos.api.innertube.RotatingHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
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
     * Obtiene visitorData sin login desde sw.js_data (como YouTube.visitorData() de OpenTune).
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
                val root = JSONObject(text)
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
                if (candidate != null) visitorData = candidate
                candidate
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun post(endpoint: String, client_: YouTubeClient, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "${YouTubeClient.API_URL_YOUTUBE_MUSIC}$endpoint?key=${YouTubeClient.INNERTUBE_API_KEY}&prettyPrint=false"
            val requestOrigin = client_.requestOrigin()
            val requestReferer = client_.requestReferer()
            val requestBuilder = Request.Builder()
                .url(url)
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
                    throw IOException(
                        "InnerTube $endpoint ${response.code} (${client_.clientName}): ${responseBody.take(300)}"
                    )
                }
                JSONObject(responseBody)
            }
        }
}
