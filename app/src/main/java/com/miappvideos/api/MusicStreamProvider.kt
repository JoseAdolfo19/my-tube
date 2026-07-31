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
