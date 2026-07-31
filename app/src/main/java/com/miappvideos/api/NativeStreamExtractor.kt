package com.miappvideos.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.miappvideos.model.AudioStream
import com.miappvideos.model.PipedStreamResponse
import com.miappvideos.model.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object NativeStreamExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getStreams(videoId: String): PipedStreamResponse? = withContext(Dispatchers.IO) {
        try {
            fetchPlayerJson(videoId)?.let { parseStreams(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchPlayerJson(videoId: String): String? {
        val urls = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            "https://m.youtube.com/watch?v=$videoId",
            "https://youtube.com/watch?v=$videoId"
        )
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: continue
                    val result = extractJson(html) ?: extractFromScripts(html, videoId)
                    if (result != null) return result
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractJson(html: String): String? {
        val markers = listOf(
            "var ytInitialPlayerResponse = ",
            "window.ytInitialPlayerResponse = ",
            "ytInitialPlayerResponse = "
        )
        for (marker in markers) {
            val start = html.indexOf(marker)
            if (start == -1) continue
            val jsonStart = start + marker.length
            var depth = 0
            var inString = false
            var escape = false
            var end = jsonStart

            for (i in jsonStart until html.length) {
                val c = html[i]
                if (escape) { escape = false; continue }
                if (c == '\\' && inString) { escape = true; continue }
                if (c == '"') { inString = !inString; continue }
                if (!inString) {
                    if (c == '{') depth++
                    if (c == '}') {
                        depth--
                        if (depth == 0) { end = i + 1; break }
                    }
                }
            }
            if (end > jsonStart) return html.substring(jsonStart, end)
        }
        return null
    }

    private fun extractFromScripts(html: String, videoId: String): String? {
        val scriptRegex = Regex("<script[^>]*>([\\s\\S]*?)</script>")
        val matches = scriptRegex.findAll(html)

        for (match in matches) {
            val scriptContent = match.groupValues[1]
            if ("player_response" in scriptContent) {
                val respMatch = Regex("\"player_response\"\\s*:\\s*\"([^\"]+)\"").find(scriptContent)
                if (respMatch != null) {
                    val escaped = respMatch.groupValues[1]
                    return escaped.replace("\\u0026", "&")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\/", "/")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                }
            }
            if ("INNERTUBE_API_KEY" in scriptContent) {
                val keyMatch = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([^\"]+)\"").find(scriptContent)
                val clientMatch = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"").find(scriptContent)
                if (keyMatch != null && clientMatch != null) {
                    val apiKey = keyMatch.groupValues[1]
                    val clientVersion = clientMatch.groupValues[1]
                    return callInnerTube(videoId, apiKey, clientVersion)
                }
            }
        }
        return fetchViaInnerTube(videoId)
    }

    private val fallbackKeys = listOf(
        "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" to "2.20240701.00.00",
        "AIzaSyA8eiZmM1FaDVjRy-Df1t1Zq2y4oZ1K1Xg" to "19.09.37"
    )

    private fun fetchViaInnerTube(videoId: String): String? {
        for ((apiKey, clientVersion) in fallbackKeys) {
            val result = callInnerTube(videoId, apiKey, clientVersion)
            if (result != null) return result
        }
        return null
    }

    private fun callInnerTube(videoId: String, apiKey: String, clientVersion: String): String? {
        try {
            val payload = """{"videoId":"$videoId","context":{"client":{"clientName":"WEB","clientVersion":"$clientVersion"}}}"""
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) return response.body?.string()
        } catch (_: Exception) {}
        return null
    }

    private fun parseStreams(playerJson: String): PipedStreamResponse? {
        try {
            val root = JsonParser.parseString(playerJson).asJsonObject

            // Check playability
            val playabilityStatus = root.getAsJsonObject("playabilityStatus")
            if (playabilityStatus != null) {
                val status = playabilityStatus.get("status")?.asString ?: ""
                if (status != "OK") return null
            }

            val streamingData = root.getAsJsonObject("streamingData") ?: return null
            val audioFormats = mutableListOf<AudioStream>()
            val videoFormats = mutableListOf<VideoStream>()

            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
            adaptiveFormats?.forEach { element ->
                val format = element.asJsonObject
                val url = format.get("url")?.asString ?: return@forEach
                val mimeType = format.get("mimeType")?.asString ?: ""
                val bitrate = format.get("bitrate")?.asInt

                if (mimeType.startsWith("audio")) {
                    audioFormats.add(AudioStream(
                        url = url,
                        format = format.get("audioQuality")?.asString,
                        quality = format.get("audioQuality")?.asString,
                        mimeType = mimeType,
                        codec = format.get("audioCodec")?.asString,
                        bitrate = bitrate,
                        initStart = null, initEnd = null, indexStart = null, indexEnd = null
                    ))
                } else {
                    videoFormats.add(VideoStream(
                        url = url,
                        format = format.get("qualityLabel")?.asString,
                        quality = format.get("qualityLabel")?.asString,
                        mimeType = mimeType,
                        codec = format.get("videoCodec")?.asString,
                        videoOnly = !mimeType.contains("audio"),
                        initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                        bitrate = bitrate,
                        width = format.get("width")?.asInt,
                        height = format.get("height")?.asInt,
                        fps = format.get("fps")?.asInt
                    ))
                }
            }

            val formats = streamingData.getAsJsonArray("formats")
            formats?.forEach { element ->
                val format = element.asJsonObject
                val url = format.get("url")?.asString ?: return@forEach
                val mimeType = format.get("mimeType")?.asString ?: ""
                videoFormats.add(VideoStream(
                    url = url,
                    format = format.get("qualityLabel")?.asString,
                    quality = format.get("qualityLabel")?.asString,
                    mimeType = mimeType,
                    codec = format.get("videoCodec")?.asString,
                    videoOnly = false,
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                    bitrate = format.get("bitrate")?.asInt,
                    width = format.get("width")?.asInt,
                    height = format.get("height")?.asInt,
                    fps = format.get("fps")?.asInt
                ))
            }

            val videoDetails = root.getAsJsonObject("videoDetails")
            return PipedStreamResponse(
                title = videoDetails?.get("title")?.asString,
                description = null, uploadDate = null, uploaderUrl = null,
                uploaderName = videoDetails?.get("author")?.asString,
                uploaderAvatar = null, thumbnailUrl = null, duration = null,
                views = null, liked = null, dislikes = null,
                audioStreams = audioFormats, videoStreams = videoFormats
            )
        } catch (_: Exception) {
            return null
        }
    }
}
