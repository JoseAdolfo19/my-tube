package com.miappvideos.api.innertube

import com.miappvideos.model.PipedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Busqueda de canciones via InnerTube (YouTube Music), sin cuotas de la
 * YouTube Data API. Portado de OpenTune (https://github.com/Arturo254/OpenTune) - GPL-3.0
 */
object InnerTubeSearch {

    private val innerTube = InnerTubeClient()

    suspend fun search(query: String): List<PipedVideo> = withContext(Dispatchers.IO) {
        try {
            StreamResolver.ensureVisitorData()
            val response = innerTube.search(query)
            parseSearchResponse(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSearchResponse(response: JSONObject): List<PipedVideo> {
        val videos = mutableListOf<PipedVideo>()
        val contents = response.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return videos

        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i) ?: continue
            val shelfContents = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
            if (shelfContents != null) {
                for (j in 0 until shelfContents.length()) {
                    val item = shelfContents.optJSONObject(j)
                        ?.optJSONObject("musicResponsiveListItemRenderer")
                    parseItem(item)?.let { videos.add(it) }
                }
                continue
            }
            val itemSection = section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
            if (itemSection != null) {
                for (j in 0 until itemSection.length()) {
                    val item = itemSection.optJSONObject(j)
                        ?.optJSONObject("musicResponsiveListItemRenderer")
                    parseItem(item)?.let { videos.add(it) }
                }
            }
        }
        return videos.distinctBy { it.url }
    }

    private fun parseItem(item: JSONObject?): PipedVideo? {
        if (item == null) return null
        val videoId = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val flexColumns = item.optJSONArray("flexColumns") ?: return null

        val title = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val subtitleRuns = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

        var uploader: String? = null
        var durationText: String? = null
        if (subtitleRuns != null) {
            val texts = mutableListOf<String>()
            for (k in 0 until subtitleRuns.length()) {
                texts.add(subtitleRuns.optJSONObject(k).optString("text", ""))
            }
            if (texts.isNotEmpty()) uploader = texts.firstOrNull()?.takeIf { it.isNotBlank() }
            val full = texts.joinToString("")
            if (full.isBlank() || full.matches(Regex(".*\\d+:\\d{2}.*"))) {
                durationText = full.takeIf { it.matches(Regex("^\\d+:\\d{2}$")) }
            }
        }

        if (durationText == null) {
            val lengthText = item.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            if (!lengthText.isNullOrBlank()) durationText = lengthText
        }

        val thumbnail = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { arr -> arr.optJSONObject(arr.length() - 1).optString("url") }
            ?.takeIf { it.isNotBlank() }

        return PipedVideo(
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            thumbnail = thumbnail,
            uploaderName = uploader,
            duration = parseDuration(durationText),
            views = null,
            uploaderAvatar = null,
            uploadedDate = null,
            shortDescription = null,
            uploaderVerified = null,
        )
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
