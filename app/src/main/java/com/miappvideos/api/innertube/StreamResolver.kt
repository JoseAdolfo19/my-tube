package com.miappvideos.api.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolucion de streams de audio via InnerTube con po_token, deobfuscacion de
 * firma/parametro n, probe de rango y fallback de clientes (portado de OpenTune).
 */
object StreamResolver {

    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    private const val MAX_CANDIDATES_PER_CLIENT = 6

    private val MAIN_CLIENT = YouTubeClient.WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS = arrayOf(
        YouTubeClient.IOS,
        YouTubeClient.MOBILE,
        YouTubeClient.ANDROID_MUSIC,
        YouTubeClient.IOS_MUSIC,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.ANDROID_TESTSUITE,
        YouTubeClient.ANDROID_UNPLUGGED,
        YouTubeClient.IPADOS,
        YouTubeClient.VISIONOS,
        YouTubeClient.TVHTML5,
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        YouTubeClient.WEB,
        YouTubeClient.WEB_CREATOR,
        YouTubeClient.WEB_REMIX,
    )

    private val innerTube = InnerTubeClient()

    private data class CachedStreamUrl(val url: String, val expiresAtMs: Long)

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Volatile
    private var syntheticPoToken: String? = null

    private var visitorDataLoaded = false

    val client: InnerTubeClient
        get() = innerTube

    suspend fun ensureVisitorData() {
        if (visitorDataLoaded) return
        visitorDataLoaded = true
        val vd = innerTube.fetchVisitorData()
        if (!vd.isNullOrBlank() && syntheticPoToken == null) {
            syntheticPoToken = PoTokenGenerator.generateColdStartToken(vd, "player")
        }
    }

    data class ResolvedStream(
        val url: String,
        val expiresInSeconds: Int?,
        val clientName: String,
        val bitrate: Int,
        val videoUrl: String? = null,
    )

    /**
     * Resuelve la URL de audio para [videoId] probando clientes en cascada.
     */
    suspend fun resolveStreamUrl(videoId: String): Result<ResolvedStream> = runCatching {
        withContext(Dispatchers.IO) {
            ensureVisitorData()
            val signatureTimestamp = runCatching {
                YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
            }.getOrNull()

            val orderedClients = buildList {
                addAll(STREAM_FALLBACK_CLIENTS)
                add(MAIN_CLIENT)
            }.distinct()

            var lastError: Throwable? = null

            for (client in orderedClients) {
                if (client.loginRequired) continue
                if (isStreamClientTemporarilyBlocked(videoId, client.clientName)) continue

                val playerResponse = runCatching {
                    innerTube.player(
                        client_ = client,
                        videoId = videoId,
                        signatureTimestamp = signatureTimestamp,
                        poToken = resolvePlayerPoToken(client),
                    )
                }.getOrNull() ?: continue

                val playabilityStatus = playerResponse.optJSONObject("playabilityStatus")
                val status = playabilityStatus?.optString("status") ?: continue
                android.util.Log.d(
                    "StreamResolver",
                    "cliente=${client.clientName} status=$status reason=${playabilityStatus.optString("reason", "")} pot=${resolvePlayerPoToken(client) != null}"
                )
                if (status != "OK") {
                    val reason = playabilityStatus.optString("reason", "")
                    if (isBotDetectionError(reason)) {
                        markStreamClientFailed(videoId, client.clientName, 403)
                    }
                    continue
                }

                val streamingData = playerResponse.optJSONObject("streamingData") ?: continue
                val expiresInSeconds = streamingData.optInt("expiresInSeconds", 0).takeIf { it > 0 }
                val formats = parseAudioFormats(streamingData.optJSONArray("adaptiveFormats"))

                if (formats.isEmpty()) continue

                var resolved: ResolvedStream? = null
                for (format in formats.take(MAX_CANDIDATES_PER_CLIENT)) {
                    val cacheKey = "$videoId:${format.itag}"
                    val cached = streamUrlCache[cacheKey]
                    val url = if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
                        cached.url
                    } else {
                        resolveFormatUrl(format, videoId, client) ?: continue
                    }
                    if (!validateStatus(url, client)) {
                        markStreamClientFailed(videoId, client.clientName, 403)
                        continue
                    }
                    val ttl = (expiresInSeconds ?: 21600) * 1000L
                    streamUrlCache[cacheKey] = CachedStreamUrl(url, System.currentTimeMillis() + ttl)

                    val videoUrl = resolveVideoUrl(streamingData, videoId, client)

                    resolved = ResolvedStream(
                        url = url,
                        expiresInSeconds = expiresInSeconds,
                        clientName = client.clientName,
                        bitrate = format.bitrate,
                        videoUrl = videoUrl,
                    )
                    break
                }
                if (resolved != null) {
                    android.util.Log.d(
                        "StreamResolver",
                        "resuelto videoId=$videoId cliente=${resolved.clientName} bitrate=${resolved.bitrate}"
                    )
                    return@withContext resolved
                }
                lastError = IOException("no format valido para ${client.clientName}")
            }
            throw lastError ?: IOException("todos los clientes InnerTube fallaron para $videoId")
        }
    }

    private fun resolvePlayerPoToken(client: YouTubeClient): String? {
        if (!innerTube.webClientPoTokenEnabled) return null
        if (!client.isWebClient()) return null
        return syntheticPoToken
    }

    private fun resolveFormatUrl(
        format: Format,
        videoId: String,
        client: YouTubeClient,
    ): String? = runCatching {
        val directUrl = format.url
        var url: String
        if (directUrl != null) {
            url = if (directUrl.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true) {
                runCatching {
                    YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, directUrl)
                }.getOrElse { directUrl }
            } else {
                directUrl
            }
        } else {
            val cipherString = format.signatureCipher ?: format.cipher ?: return null
            val params = parseQueryString(cipherString)
            val obfuscatedSignature = params["s"] ?: return null
            val signatureParam = params["sp"] ?: return null
            val baseUrl = params["url"] ?: return null
            val signature = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
            val urlBuilder = baseUrl.toHttpUrlOrNull() ?: return null
            url = urlBuilder.newBuilder().addQueryParameter(signatureParam, signature).build().toString()
            url = runCatching {
                YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
            }.getOrElse { url }
        }

        url = patchClientVersion(url, client.clientVersion)

        val token = resolvePlayerPoToken(client)
        if (token != null && url.contains("pot=").not()) {
            val separator = if (url.contains("?")) "&" else "?"
            url = "$url${separator}pot=$token"
        }
        url
    }.getOrNull()

    private fun patchClientVersion(url: String, clientVersion: String): String {
        if (!url.contains("cver=")) return url
        return url.replace(Regex("cver=[^&]+"), "cver=$clientVersion")
    }

    /**
     * Valida la URL con probes de rango; para clientes web incluye el probe del
     * limite de 1 MB (el clasico corte a 1:05).
     */
    private fun validateStatus(url: String, client: YouTubeClient): Boolean {
        return try {
            val clientParam = url.toHttpUrlOrNull()?.queryParameter("c").orEmpty().uppercase(Locale.US)
            val isWeb = clientParam.isNotEmpty() && (
                clientParam.contains("WEB") || clientParam.contains("TVHTML5")
                )

            val userAgent = resolveUserAgent(clientParam, client)
            val originReferer = resolveOriginReferer(clientParam)

            val probeRanges =
                if (isWeb) {
                    listOf("bytes=0-0", "bytes=262144-262145", "bytes=1048576-1048577")
                } else {
                    listOf("bytes=0-0")
                }

            for (range in probeRanges) {
                val request = okhttp3.Request.Builder()
                    .get()
                    .header("User-Agent", userAgent)
                    .header("Range", range)
                    .apply {
                        originReferer.first?.let { header("Origin", it) }
                        originReferer.second?.let { header("Referer", it) }
                    }
                    .url(url)
                    .build()
                val code = probeClient.newCall(request).execute().use { it.code }
                if (code == 403) return false
                if (code !in 200..399 && code != 416) return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveUserAgent(clientParam: String, fallback: YouTubeClient): String {
        return when {
            clientParam == "WEB_REMIX" || clientParam == "WEB" || clientParam == "MWEB" ||
                clientParam == "WEB_EMBEDDED_PLAYER" || clientParam == "WEB_CREATOR" -> YouTubeClient.USER_AGENT_WEB
            clientParam == "TVHTML5" || clientParam == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" ->
                "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15"
            clientParam.startsWith("ANDROID_MUSIC") -> YouTubeClient.ANDROID_MUSIC.userAgent
            clientParam.startsWith("ANDROID_VR") -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent
            clientParam.startsWith("IOS_MUSIC") -> YouTubeClient.IOS_MUSIC.userAgent
            clientParam.startsWith("IOS") -> YouTubeClient.IOS.userAgent
            clientParam.startsWith("ANDROID") -> YouTubeClient.MOBILE.userAgent
            else -> fallback.userAgent
        }
    }

    private fun resolveOriginReferer(clientParam: String): Pair<String?, String?> {
        return when {
            clientParam == "TVHTML5" || clientParam == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" ->
                YouTubeClient.ORIGIN_YOUTUBE to YouTubeClient.REFERER_YOUTUBE_TV
            clientParam.startsWith("WEB") || clientParam.startsWith("MWEB") ->
                YouTubeClient.ORIGIN_YOUTUBE_MUSIC to YouTubeClient.REFERER_YOUTUBE_MUSIC
            else -> null to null
        }
    }

    /**
     * Resuelve una URL de video para el mismo [videoId]: prefiere el formato
     * muxed (formats[], itag 18) y si no existe, el video-only de menor
     * resolucion (suficiente para musica).
     */
    private fun resolveVideoUrl(
        streamingData: JSONObject,
        videoId: String,
        client: YouTubeClient,
    ): String? {
        val candidates = parseVideoFormats(streamingData)
        for (format in candidates.take(3)) {
            val cacheKey = "v:$videoId:${format.itag}"
            val cached = streamUrlCache[cacheKey]
            val url = if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
                cached.url
            } else {
                resolveFormatUrl(format, videoId, client) ?: continue
            }
            if (!validateStatus(url, client)) continue
            streamUrlCache[cacheKey] =
                CachedStreamUrl(url, System.currentTimeMillis() + 21600 * 1000L)
            return url
        }
        return null
    }

    private data class Format(
        val itag: Int,
        val url: String?,
        val mimeType: String,
        val bitrate: Int,
        val signatureCipher: String?,
        val cipher: String?,
        val height: Int = 0,
    )

    private fun parseAudioFormats(jsonArray: org.json.JSONArray?): List<Format> {
        if (jsonArray == null) return emptyList()
        val result = mutableListOf<Format>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            if (item.has("width")) continue
            val url = item.optString("url").takeIf { it.isNotBlank() }
            val signatureCipher = item.optString("signatureCipher").takeIf { it.isNotBlank() }
            val cipher = item.optString("cipher").takeIf { it.isNotBlank() }
            if (url == null && signatureCipher == null && cipher == null) continue
            val bitrate = item.optInt("bitrate", 0)
            if (bitrate <= 0) continue
            result.add(
                Format(
                    itag = item.optInt("itag", 0),
                    url = url,
                    mimeType = item.optString("mimeType", ""),
                    bitrate = bitrate,
                    signatureCipher = signatureCipher,
                    cipher = cipher,
                )
            )
        }
        return result.sortedByDescending { it.bitrate }
    }

    private fun parseVideoFormats(streamingData: JSONObject): List<Format> {
        val result = mutableListOf<Format>()

        val muxed = streamingData.optJSONArray("formats")
        if (muxed != null) {
            for (i in 0 until muxed.length()) {
                val item = muxed.optJSONObject(i) ?: continue
                if (item.optInt("width", 0) <= 0) continue
                val url = item.optString("url").takeIf { it.isNotBlank() }
                val signatureCipher = item.optString("signatureCipher").takeIf { it.isNotBlank() }
                val cipher = item.optString("cipher").takeIf { it.isNotBlank() }
                if (url == null && signatureCipher == null && cipher == null) continue
                result.add(
                    Format(
                        itag = item.optInt("itag", 0),
                        url = url,
                        mimeType = item.optString("mimeType", ""),
                        bitrate = item.optInt("bitrate", 0),
                        signatureCipher = signatureCipher,
                        cipher = cipher,
                        height = item.optInt("height", 0),
                    )
                )
            }
        }
        if (result.isNotEmpty()) return result.sortedByDescending { it.height }

        val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return emptyList()
        for (i in 0 until adaptive.length()) {
            val item = adaptive.optJSONObject(i) ?: continue
            val width = item.optInt("width", 0)
            if (width <= 0) continue
            val url = item.optString("url").takeIf { it.isNotBlank() }
            val signatureCipher = item.optString("signatureCipher").takeIf { it.isNotBlank() }
            val cipher = item.optString("cipher").takeIf { it.isNotBlank() }
            if (url == null && signatureCipher == null && cipher == null) continue
            result.add(
                Format(
                    itag = item.optInt("itag", 0),
                    url = url,
                    mimeType = item.optString("mimeType", ""),
                    bitrate = item.optInt("bitrate", 0),
                    signatureCipher = signatureCipher,
                    cipher = cipher,
                    height = item.optInt("height", 0),
                )
            )
        }
        return result.sortedBy { it.height }
    }

    private fun parseQueryString(cipher: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        cipher.split("&").forEach { pair ->
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1).replace("%3D", "=").replace("%26", "&").replace("%25", "%")
                params[key] = value
            }
        }
        return params
    }

    private fun markStreamClientFailed(videoId: String, clientKey: String?, httpStatusCode: Int?) {
        if (httpStatusCode != 403) return
        val normalized = clientKey?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.US).orEmpty()
        if (normalized.isEmpty()) return
        failedStreamClientsUntil["$videoId:$normalized"] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
    }

    private fun isStreamClientTemporarilyBlocked(videoId: String, clientKey: String?): Boolean {
        val normalized = clientKey?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.US).orEmpty()
        if (normalized.isEmpty()) return false
        val until = failedStreamClientsUntil["$videoId:$normalized"] ?: return false
        if (until <= System.currentTimeMillis()) {
            failedStreamClientsUntil.remove("$videoId:$normalized")
            return false
        }
        return true
    }

    private fun isBotDetectionError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "bot" in lower ||
            "unusual traffic" in lower ||
            "automated" in lower ||
            ("confirm" in lower && "not a" in lower) ||
            "not a robot" in lower ||
            ("verify" in lower && "human" in lower)
    }
}
