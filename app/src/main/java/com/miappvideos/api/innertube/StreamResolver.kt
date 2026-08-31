package com.miappvideos.api.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private const val RESOLVE_TIMEOUT_MS = 12_000L

    // Clientes que devuelven URLs directas sin po_token (confirmado en pruebas).
    // Se sondean en paralelo para que el arranque no dependa del fallo anti-bot
    // de una sola version. ANDROID/MOBILE devuelve formatos pot-gated sin url.
    private val PARALLEL_PROBE_CLIENTS = arrayOf(
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_1_43_32,
    )

    private val MAIN_CLIENT = YouTubeClient.WEB_REMIX

    // Orden optimizado para arranque rapido: primero los clientes que funcionan
    // sin login ni po_token (ANDROID_VR preferido, luego MOBILE). Los clientes
    // web (WEB/WEB_REMIX) van al final porque exigen po_token/visitorData.
    private val STREAM_FALLBACK_CLIENTS = arrayOf(
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.MOBILE,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_MUSIC,
        YouTubeClient.IOS_MUSIC,
        YouTubeClient.IOS,
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
        .connectTimeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
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

    data class VideoQuality(
        val itag: Int,
        val label: String,
        val url: String,
    )

    data class ResolvedStream(
        val url: String,
        val expiresInSeconds: Int?,
        val clientName: String,
        val bitrate: Int,
        val videoUrl: String? = null,
        val videoQualities: List<VideoQuality> = emptyList(),
    )

    data class DownloadStreams(
        val audioUrl: String,
        val audioMime: String,
        val muxedUrl: String?,
        val muxedMime: String?,
        val clientName: String,
    )

    /**
     * Resuelve URLs para descarga: audio-only (preferible AAC/m4a) y el formato
     * muxed (video+audio, itag 18) para descargar un .mp4 listo.
     */
    suspend fun resolveDownloadStreams(videoId: String): Result<DownloadStreams> = runCatching {
        withTimeout(RESOLVE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            ensureVisitorData()
            var signatureTimestamp: Int? = null

            val orderedClients = buildList {
                addAll(STREAM_FALLBACK_CLIENTS)
                add(MAIN_CLIENT)
            }.distinct()

            var lastError: Throwable? = null

            for (client in orderedClients) {
                if (client.loginRequired) continue
                if (isStreamClientTemporarilyBlocked(videoId, clientBlockKey(client))) continue

                val ts = if (client.useSignatureTimestamp) {
                    signatureTimestamp ?: runCatching {
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
                    }.getOrNull()?.also { signatureTimestamp = it }
                } else null

                val playerResponse = runCatching {
                    innerTube.player(
                        client_ = client,
                        videoId = videoId,
                        signatureTimestamp = ts,
                        poToken = resolvePlayerPoToken(client),
                    )
                }.getOrNull() ?: continue

                val playabilityStatus = playerResponse.optJSONObject("playabilityStatus")
                if (playabilityStatus?.optString("status") != "OK") continue
                val streamingData = playerResponse.optJSONObject("streamingData") ?: continue

                val audio = pickDownloadAudio(streamingData.optJSONArray("adaptiveFormats")) ?: continue
                val audioUrl = resolveDownloadFormatUrl(audio, videoId, client) ?: continue
                if (!validateStatus(audioUrl, client)) {
                    markStreamClientFailed(videoId, clientBlockKey(client), 403)
                    continue
                }

                val muxed = pickMuxedFormat(streamingData.optJSONArray("formats"))
                val muxedUrl = muxed?.let {
                    val u = resolveDownloadFormatUrl(it, videoId, client) ?: return@let null
                    if (validateStatus(u, client)) u else null
                }

                android.util.Log.d(
                    "StreamResolver",
                    "descarga resuelto videoId=$videoId cliente=${client.clientName} audio=${audio.mimeType} muxed=${muxedUrl != null}"
                )
                return@withContext DownloadStreams(
                    audioUrl = audioUrl,
                    audioMime = audio.mimeType.ifBlank { "audio/mp4" },
                    muxedUrl = muxedUrl,
                    muxedMime = muxed?.mimeType,
                    clientName = client.clientName,
                )
            }
            throw lastError ?: IOException("todos los clientes InnerTube fallaron para $videoId")
        }
        }
    }

    private fun resolveDownloadFormatUrl(format: Format, videoId: String, client: YouTubeClient): String? {
        val raw = resolveFormatUrlRaw(format, client)
        if (raw != null && validateStatus(raw, client)) return raw
        val deobfuscated = resolveFormatUrlDeobfuscated(format, videoId, client)
        if (deobfuscated != null && validateStatus(deobfuscated, client)) return deobfuscated
        return raw ?: deobfuscated
    }

    data class DownloadOption(
        val label: String,
        val url: String,
        val mime: String,
        val ext: String,
    )

    data class DownloadOptions(
        val audio: List<DownloadOption>,
        val video: List<DownloadOption>,
    )

    /**
     * Resuelve TODAS las opciones de descarga disponibles: audios por bitrate y
     * videos por resolucion. Para videos prefiere muxed (con audio incluido).
     */
    suspend fun resolveDownloadOptions(videoId: String): Result<DownloadOptions> = runCatching {
        withTimeout(RESOLVE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            ensureVisitorData()
            var signatureTimestamp: Int? = null
            val orderedClients = buildList {
                addAll(STREAM_FALLBACK_CLIENTS)
                add(MAIN_CLIENT)
            }.distinct()

            for (client in orderedClients) {
                if (client.loginRequired) continue
                if (isStreamClientTemporarilyBlocked(videoId, clientBlockKey(client))) continue

                val ts = if (client.useSignatureTimestamp) {
                    signatureTimestamp ?: runCatching {
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
                    }.getOrNull()?.also { signatureTimestamp = it }
                } else null

                val playerResponse = runCatching {
                    innerTube.player(
                        client_ = client,
                        videoId = videoId,
                        signatureTimestamp = ts,
                        poToken = resolvePlayerPoToken(client),
                    )
                }.getOrNull() ?: continue

                if (playerResponse.optJSONObject("playabilityStatus")?.optString("status") != "OK") continue
                val streamingData = playerResponse.optJSONObject("streamingData") ?: continue

                val audio = parseAudioFormats(streamingData.optJSONArray("adaptiveFormats"))
                    .mapNotNull { f ->
                        val url = resolveFormatUrlRaw(f, client) ?: return@mapNotNull null
                        val kbps = (f.bitrate / 1000).coerceAtLeast(1)
                        DownloadOption(
                            label = "MP3 $kbps kbps",
                            url = url,
                            mime = f.mimeType.substringBefore(";").ifBlank { "audio/mp4" },
                            ext = "mp3",
                        )
                    }

                val muxedAvailable = (streamingData.optJSONArray("formats")?.length() ?: 0) > 0
                val video = parseVideoFormats(streamingData)
                    .filter { it.height > 0 }
                    .groupBy { it.height }
                    .map { (_, g) -> g.maxByOrNull { it.bitrate }!! }
                    .sortedByDescending { it.height }
                    .mapNotNull { f ->
                        val url = resolveFormatUrlRaw(f, client) ?: return@mapNotNull null
                        val suffix = if (muxedAvailable) "" else " (sin audio)"
                        val ext = if (f.mimeType.contains("webm")) "webm" else "mp4"
                        DownloadOption(
                            label = "MP4 ${f.height}p$suffix",
                            url = url,
                            mime = f.mimeType.substringBefore(";").ifBlank { "video/mp4" },
                            ext = ext,
                        )
                    }

                if (audio.isEmpty()) continue

                android.util.Log.d(
                    "StreamResolver",
                    "download options videoId=$videoId cliente=${client.clientName} audios=${audio.size} videos=${video.size}"
                )
                return@withContext DownloadOptions(audio = audio, video = video)
            }
            throw IOException("sin opciones de descarga para $videoId")
        }
        }
    }

    private fun pickDownloadAudio(jsonArray: org.json.JSONArray?): Format? {
        val formats = parseAudioFormats(jsonArray)
        if (formats.isEmpty()) return null
        return formats.firstOrNull { it.mimeType.contains("audio/mp4") } ?: formats.first()
    }

    private fun pickMuxedFormat(jsonArray: org.json.JSONArray?): Format? {
        if (jsonArray == null) return null
        val muxed = mutableListOf<Format>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            if (item.optInt("width", 0) <= 0) continue
            val url = item.optString("url").takeIf { it.isNotBlank() }
            val signatureCipher = item.optString("signatureCipher").takeIf { it.isNotBlank() }
            val cipher = item.optString("cipher").takeIf { it.isNotBlank() }
            if (url == null && signatureCipher == null && cipher == null) continue
            muxed.add(
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
        if (muxed.isEmpty()) return null
        return muxed.firstOrNull { it.itag == 18 }
            ?: muxed.minByOrNull { it.height }
    }

    private val inflightResolve = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Result<ResolvedStream>>>()

    /**
     * Resuelve la URL de audio para [videoId] probando clientes en cascada.
     * [PARALLEL_PROBE_CLIENTS] se sondean en paralelo para que el arranque no
     * dependa del fallo anti-bot de una sola version.
     * Se deduplica por videoId (single-flight): varias llamadas concurrentes
     * al mismo video comparten una sola resolucion.
     */
    suspend fun resolveStreamUrl(videoId: String): Result<ResolvedStream> {
        inflightResolve[videoId]?.let { return it.await() }
        return coroutineScope {
            val deferred = async(Dispatchers.IO) { resolveStreamUrlInternal(videoId) }
            inflightResolve[videoId] = deferred
            try {
                deferred.await()
            } finally {
                inflightResolve.remove(videoId, deferred)
            }
        }
    }

    private suspend fun resolveStreamUrlInternal(videoId: String): Result<ResolvedStream> = runCatching {
        withTimeout(RESOLVE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            ensureVisitorData()

            val orderedClients = buildList {
                addAll(STREAM_FALLBACK_CLIENTS)
                add(MAIN_CLIENT)
            }.distinct()

            val parallelClients = PARALLEL_PROBE_CLIENTS
                .filter { !it.loginRequired && !isStreamClientTemporarilyBlocked(videoId, clientBlockKey(it)) }

            val needsTimestamp = parallelClients.any { it.useSignatureTimestamp }
            val signatureTimestamp = if (needsTimestamp) {
                runCatching {
                    YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
                }.getOrNull()
            } else null

            val parallelResults = coroutineScope {
                parallelClients.map { client ->
                    async { tryResolveForClient(videoId, client, signatureTimestamp) }
                }.awaitAll()
            }
            parallelResults.firstOrNull { it != null }?.let { return@withContext it }

            val probedKeys = parallelClients.map { clientBlockKey(it) }.toSet()
            for (client in orderedClients) {
                if (client.loginRequired) continue
                if (clientBlockKey(client) in probedKeys) continue
                if (isStreamClientTemporarilyBlocked(videoId, clientBlockKey(client))) continue
                val resolved = tryResolveForClient(videoId, client, signatureTimestamp) ?: continue
                return@withContext resolved
            }
            throw IOException("todos los clientes InnerTube fallaron para $videoId")
        }
        }
    }

    private suspend fun tryResolveForClient(
        videoId: String,
        client: YouTubeClient,
        signatureTimestamp: Int?,
    ): ResolvedStream? {
        val ts = if (client.useSignatureTimestamp) signatureTimestamp else null

        val playerResponse = runCatching {
            innerTube.player(
                client_ = client,
                videoId = videoId,
                signatureTimestamp = ts,
                poToken = resolvePlayerPoToken(client),
            )
        }.getOrNull() ?: return null

        val playabilityStatus = playerResponse.optJSONObject("playabilityStatus")
        val status = playabilityStatus?.optString("status") ?: return null
        android.util.Log.d(
            "StreamResolver",
            "cliente=${client.clientName} status=$status reason=${playabilityStatus.optString("reason", "")} pot=${resolvePlayerPoToken(client) != null}"
        )
        if (status != "OK") {
            val reason = playabilityStatus.optString("reason", "")
            if (isBotDetectionError(reason)) {
                markStreamClientFailed(videoId, clientBlockKey(client), 403)
            }
            return null
        }

        val streamingData = playerResponse.optJSONObject("streamingData") ?: return null
        val expiresInSeconds = streamingData.optInt("expiresInSeconds", 0).takeIf { it > 0 }
        val formats = parseAudioFormats(streamingData.optJSONArray("adaptiveFormats"))

        if (formats.isEmpty()) {
            val arr = streamingData.optJSONArray("adaptiveFormats")
            var sinWidth = 0
            var sample: String? = null
            for (i in 0 until (arr?.length() ?: 0)) {
                val item = arr.optJSONObject(i) ?: continue
                if (!item.has("width")) {
                    sinWidth++
                    if (sample == null) sample = item.toString().take(400)
                }
            }
            android.util.Log.d(
                "StreamResolver",
                "cliente=${client.clientName} SIN formatos adaptive=${arr?.length() ?: -1} audioCandidatos=$sinWidth ejemplo=$sample"
            )
            return null
        }

        var resolved: ResolvedStream? = null
        resolved = coroutineScope {
            formats.take(3).map { format ->
                async { resolveFormatForVideo(videoId, format, client, expiresInSeconds, streamingData) }
            }.awaitAll().firstOrNull { it != null }
        }
        if (resolved != null) {
            android.util.Log.d(
                "StreamResolver",
                "resuelto videoId=$videoId cliente=${resolved.clientName} bitrate=${resolved.bitrate}"
            )
        }
        return resolved
    }

    private suspend fun resolveFormatForVideo(
        videoId: String,
        format: Format,
        client: YouTubeClient,
        expiresInSeconds: Int?,
        streamingData: JSONObject,
    ): ResolvedStream? {
        val cacheKey = "$videoId:${format.itag}"
        val now = System.currentTimeMillis()
        val cached = streamUrlCache[cacheKey]
        val cachedFresh = cached?.expiresAtMs?.let { it > now } == true

        var url: String? = if (cachedFresh) cached!!.url else resolveFormatUrlRaw(format, client)
        if (!cachedFresh) {
            if (url == null || !validateStatus(url, client)) {
                val deobfuscated = resolveFormatUrlDeobfuscated(format, videoId, client)
                url = if (deobfuscated != null && validateStatus(deobfuscated, client)) {
                    deobfuscated
                } else {
                    null
                }
            }
            if (url == null) {
                android.util.Log.d(
                    "StreamResolver",
                    "cliente=${client.clientName} itag=${format.itag} URL_INVALIDA"
                )
                markStreamClientFailed(videoId, clientBlockKey(client), 403)
                return null
            }
        }
        val resolvedUrl = url!!

        val ttl = (expiresInSeconds ?: 21600) * 1000L
        streamUrlCache[cacheKey] = CachedStreamUrl(resolvedUrl, now + ttl)

        val videoUrl = resolveVideoUrl(streamingData, videoId, client)
        val qualities = collectVideoQualities(streamingData, client)

        return ResolvedStream(
            url = resolvedUrl,
            expiresInSeconds = expiresInSeconds,
            clientName = client.clientName,
            bitrate = format.bitrate,
            videoUrl = videoUrl,
            videoQualities = qualities,
        )
    }

    /**
     * Recolecta las calidades de video disponibles (muxed + video-only) con URL
     * directa. Deduplica por altura y ordena de mayor a menor resolucion.
     */
    private fun collectVideoQualities(
        streamingData: JSONObject,
        client: YouTubeClient,
    ): List<VideoQuality> {
        return runCatching {
            parseVideoFormats(streamingData)
                .filter { it.height > 0 }
                .groupBy { it.height }
                .map { (_, group) -> group.maxByOrNull { it.bitrate }!! }
                .sortedByDescending { it.height }
                .mapNotNull { f ->
                    val url = resolveFormatUrlRaw(f, client) ?: return@mapNotNull null
                    VideoQuality(itag = f.itag, label = "${f.height}p", url = url)
                }
        }.getOrDefault(emptyList())
    }

    private fun resolvePlayerPoToken(client: YouTubeClient): String? {
        if (!innerTube.webClientPoTokenEnabled) return null
        if (!client.isWebClient()) return null
        return syntheticPoToken
    }

    private fun resolveFormatUrlRaw(format: Format, client: YouTubeClient): String? {
        val directUrl = format.url ?: return null
        return patchUrl(directUrl, client)
    }

    private fun resolveFormatUrlDeobfuscated(
        format: Format,
        videoId: String,
        client: YouTubeClient,
    ): String? = runCatching {
        val directUrl = format.url
        var url: String
        if (directUrl != null) {
            url = runCatching {
                YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, directUrl)
            }.getOrElse { directUrl }
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
        patchUrl(url, client)
    }.getOrNull()

    private fun patchUrl(url: String, client: YouTubeClient): String {
        var result = patchClientVersion(url, client.clientVersion)
        val token = resolvePlayerPoToken(client)
        if (token != null && result.contains("pot=").not()) {
            val separator = if (result.contains("?")) "&" else "?"
            result = "$result${separator}pot=$token"
        }
        return result
    }

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
            val now = System.currentTimeMillis()
            val cached = streamUrlCache[cacheKey]
            val cachedFresh = cached?.expiresAtMs?.let { it > now } == true
            val url = if (cachedFresh) {
                cached!!.url
            } else {
                resolveVideoFormatUrl(format, videoId, client) ?: continue
            }
            if (!cachedFresh && !validateStatus(url, client)) continue
            streamUrlCache[cacheKey] = CachedStreamUrl(url, now + 21600 * 1000L)
            return url
        }
        return null
    }

    private fun resolveVideoFormatUrl(format: Format, videoId: String, client: YouTubeClient): String? {
        val raw = resolveFormatUrlRaw(format, client)
        if (raw != null && validateStatus(raw, client)) return raw
        val deobfuscated = resolveFormatUrlDeobfuscated(format, videoId, client)
        if (deobfuscated != null && validateStatus(deobfuscated, client)) return deobfuscated
        return raw ?: deobfuscated
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

    private fun clientBlockKey(client: YouTubeClient): String =
        "${client.clientName}#${client.clientVersion}"

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
