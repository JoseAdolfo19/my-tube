package com.miappvideos.api.innertube

import com.miappvideos.model.PipedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Busqueda de canciones via InnerTube (YouTube), sin cuotas de la YouTube Data API.
 * El search de YouTube Music sin login solo devuelve playlists/albumes, por lo que
 * se usa el search de YouTube normal (client WEB) que devuelve videoRenderer con
 * canciones individuales.
 * Portado de OpenTune (https://github.com/Arturo254/OpenTune) - GPL-3.0
 */
object InnerTubeSearch {

    private val innerTube = InnerTubeClient()

    suspend fun search(query: String): List<PipedVideo> = withContext(Dispatchers.IO) {
        try {
            StreamResolver.ensureVisitorData()
            val response = innerTube.searchVideos(query)
            val parsed = parseSearchResponse(response)
            android.util.Log.d("InnerTubeSearch", "query=$query videos=${parsed.size}")
            parsed
        } catch (e: Exception) {
            android.util.Log.d("InnerTubeSearch", "query=$query error=${e.message}")
            emptyList()
        }
    }

    private fun parseSearchResponse(response: JSONObject): List<PipedVideo> {
        val videos = mutableListOf<PipedVideo>()
        val contents = response.optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
        if (contents == null) {
            android.util.Log.d("InnerTubeSearch", "sin twoColumnSearchResults: ${response.toString().take(400)}")
            return videos
        }

        for (i in 0 until contents.length()) {
            val itemSection = contents.optJSONObject(i)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")
                ?: continue
            for (j in 0 until itemSection.length()) {
                val videoRenderer = itemSection.optJSONObject(j)
                    ?.optJSONObject("videoRenderer")
                parseVideoRenderer(videoRenderer)?.let { videos.add(it) }
            }
        }
        return videos.distinctBy { it.url }
    }

    private fun parseVideoRenderer(vr: JSONObject?): PipedVideo? {
        if (vr == null) return null
        val videoId = vr.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val title = vr.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val owner = vr.optJSONObject("ownerText")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }

        val durationText = vr.optJSONObject("lengthText")
            ?.optString("simpleText")
            ?.takeIf { it.isNotBlank() }

        val thumbnail = vr.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { arr -> arr.optJSONObject(arr.length() - 1).optString("url") }
            ?.takeIf { it.isNotBlank() }

        val viewsText = vr.optJSONObject("viewCountText")?.optString("simpleText")
        val views = parseViews(viewsText)

        return PipedVideo(
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            thumbnail = thumbnail,
            uploaderName = owner,
            duration = parseDuration(durationText),
            views = views,
            uploaderAvatar = null,
            uploadedDate = null,
            shortDescription = null,
            uploaderVerified = null,
        )
    }

    private fun parseViews(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.toLongOrNull()
    }

    private fun parseDuration(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(":")
        if (parts.size == 2) {
            return parts[0].toLongOrNull()?.times(60)?.plus(parts[1].toLongOrNull() ?: 0)
        }
        if (parts.size == 3) {
            return parts[0].toLongOrNull()?.times(3600)?.plus(parts[1].toLongOrNull()?.times(60) ?: 0)
                ?.plus(parts[2].toLongOrNull() ?: 0)
        }
        return null
    }
}
