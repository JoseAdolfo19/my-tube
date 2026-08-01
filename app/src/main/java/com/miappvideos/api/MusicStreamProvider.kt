package com.miappvideos.api

import android.util.Log
import com.miappvideos.api.innertube.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Orquestador de streams de audio.
 *
 * Orden de resolucion:
 *  1. StreamResolver (InnerTube directo con po_token, deobfuscacion de firma
 *     y parametro n, probe de rango y fallback de clientes): funciona desde
 *     cualquier IP residencial sin servidor externo.
 *  2. Proxy (tools/audio_proxy.py en el PC o Render): util cuando la red del
 *     celular no deja salir directo o como respaldo.
 *  3. Fallback al StreamProvider existente (Piped + NewPipeExtractor).
 */
object MusicStreamProvider {

    private const val TAG = "MusicStreamProvider"
    private const val PROXY_LAN = "http://192.168.187.240:8080"
    private const val PROXY_USB = "http://127.0.0.1:8080"
    private const val PROXY_PUBLIC = "https://mytube-proxy-q284.onrender.com"
    private const val PROXY_KEY = "mytube-2026-proxy"

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private val cache = mutableMapOf<String, String?>()

    data class StreamResult(
        val audioUrl: String,
        val videoUrl: String?,
        val clientName: String? = null,
    )

    suspend fun getStream(videoId: String): StreamResult? = withContext(Dispatchers.IO) {
        fetchStreamFromInnerTube(videoId)
            ?: fetchStreamFromProxy(videoId)
            ?: fetchStreamFromExistingProvider(videoId)
    }

    suspend fun getAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        cache[videoId] ?: run {
            val result = fetchFromInnerTube(videoId)
                ?: fetchFromProxy(videoId)
                ?: fetchFromExistingProvider(videoId)
            cache[videoId] = result
            result
        }
    }

    suspend fun preload(vararg videoIds: String) {
        for (id in videoIds) {
            if (id !in cache) {
                val url = fetchFromInnerTube(id) ?: fetchFromProxy(id) ?: fetchFromExistingProvider(id)
                cache[id] = url
            }
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private suspend fun fetchStreamFromInnerTube(videoId: String): StreamResult? {
        val result = StreamResolver.resolveStreamUrl(videoId)
        return result.getOrNull()?.let {
            Log.d(TAG, "InnerTube OK (${it.clientName}) para $videoId video=${it.videoUrl != null}")
            StreamResult(audioUrl = it.url, videoUrl = it.videoUrl, clientName = it.clientName)
        }
    }

    private fun fetchStreamFromProxy(videoId: String): StreamResult? {
        val url = fetchFromProxy(videoId) ?: return null
        return StreamResult(audioUrl = url, videoUrl = null)
    }

    private suspend fun fetchStreamFromExistingProvider(videoId: String): StreamResult? {
        return try {
            val streams = StreamProvider.getStreams(videoId) ?: return null
            val audioUrl = streams.audioStreams?.maxByOrNull { it.bitrate ?: 0 }?.url ?: return null
            val muxed = streams.videoStreams?.filter { it.videoOnly == false }
            val videoUrl = (muxed ?: emptyList()).maxByOrNull { it.height ?: 0 }?.url
            StreamResult(audioUrl = audioUrl, videoUrl = videoUrl)
        } catch (e: Exception) {
            Log.d(TAG, "Fallback fallo para $videoId: ${e.message}")
            null
        }
    }

    private suspend fun fetchFromInnerTube(videoId: String): String? {
        val result = StreamResolver.resolveStreamUrl(videoId)
        return result.getOrNull()?.url?.also {
            Log.d(TAG, "InnerTube OK (${result.getOrNull()?.clientName}) para $videoId")
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
