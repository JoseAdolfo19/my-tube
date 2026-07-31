package com.miappvideos.api

import android.util.Log
import com.miappvideos.api.innertube.ClientProfile
import com.miappvideos.api.innertube.ClientProfiles
import com.miappvideos.api.innertube.RotatingHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Orquestador de streams enfocado en audio (estilo OpenTune, codigo propio).
 *
 * Orden de resolucion:
 *  1. Proxy local (tools/audio_proxy.py en el PC): resuelve la URL con
 *     po_token via yt-dlp y hace passthrough de rangos, evitando el limite
 *     de 1 MB de googlevideo (sin potoken la musica se corta a ~1:05).
 *  2. InnerTube directo (music.youtube.com) probando los 3 perfiles de ClientProfiles.
 *  3. Fallback al StreamProvider existente (Piped + NewPipeExtractor).
 *
 * Solo se extrae audio (la app es de musica); los resultados se ordenan por
 * bitrate descendente.
 */
object MusicStreamProvider {

    private const val TAG = "MusicStreamProvider"
    private const val JSON_TYPE = "application/json; charset=utf-8"
    private const val PROXY_LAN = "http://192.168.187.240:8080"
    private const val PROXY_USB = "http://127.0.0.1:8080"
    private const val PROXY_PUBLIC = "https://mytube-proxy.onrender.com"
    private const val PROXY_KEY = ""

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private val cache = mutableMapOf<String, String?>()

    suspend fun getAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        cache[videoId] ?: run {
            val result = fetchFromProxy(videoId)
                ?: fetchFromInnerTube(videoId)
                ?: fetchFromExistingProvider(videoId)
            cache[videoId] = result
            result
        }
    }

    private fun fetchFromProxy(videoId: String): String? {
        for (base in listOf(PROXY_LAN, PROXY_USB, PROXY_PUBLIC)) {
            try {
                val url = "$base/audio?v=$videoId" + if (PROXY_KEY.isNotEmpty()) "&key=$PROXY_KEY" else ""
                val probe = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                probeClient.newCall(probe).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Proxy OK ($base) para $videoId")
                        return url
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Proxy $base no disponible para $videoId: ${e.message}")
            }
        }
        return null
    }

    suspend fun preload(vararg videoIds: String) {
        for (id in videoIds) {
            if (id !in cache) {
                val url = fetchFromProxy(id) ?: fetchFromInnerTube(id) ?: fetchFromExistingProvider(id)
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
