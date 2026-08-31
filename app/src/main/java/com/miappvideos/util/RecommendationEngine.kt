package com.miappvideos.util

import com.miappvideos.api.PipedApi
import com.miappvideos.api.YouTubeDataManager
import com.miappvideos.api.innertube.InnerTubeSearch
import com.miappvideos.model.PipedVideo
import kotlin.random.Random

/**
 * Motor de recomendaciones y gestión de cola para MyTube.
 * Se encarga de obtener candidatos de diversas fuentes, filtrarlos por calidad musical,
 * evitar duplicados y reordenarlos localmente para evitar rachas del mismo artista.
 */
class RecommendationEngine(
    private val api: PipedApi,
    private val youTubeManager: YouTubeDataManager
) {
    private val accent = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
        'ü' to 'u', 'ñ' to 'n'
    )

    private fun normalize(text: String?): String {
        var t = (text ?: "").lowercase()
        for ((k, v) in accent) t = t.replace(k, v)
        return t.trim()
    }

    fun normalizeSongTitle(title: String?): String {
        var t = normalize(title)
        t = t.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), " ")
        t = t.replace(Regex("\\|"), " - ")
        t = t.replace(Regex("\\b(letra|lyrics|lyric|official video|video oficial|official|hd|4k|audio|video|live|concierto|sesion|en vivo|musical)\\b"), " ")
        t = t.replace(Regex("[-–—]"), " ")
        t = t.replace(Regex("[^a-z0-9 ]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun sameSong(a: String?, b: String?): Boolean {
        val na = normalizeSongTitle(a)
        val nb = normalizeSongTitle(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val short = if (na.length <= nb.length) na else nb
        val long = if (na.length <= nb.length) nb else na
        return short.length >= 5 && long.contains(short)
    }

    fun normalizeAuthor(name: String?): String {
        var t = normalize(name)
        t = t.replace("vevo", "")
        t = t.replace("- topic", "")
        t = t.replace(" topic", "")
        t = t.replace("official", "")
        t = t.replace("topics", "")
        t = t.replace(Regex("[\\[\\]()\\-]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun sameAuthor(a: String?, b: String?): Boolean {
        val na = normalizeAuthor(a)
        val nb = normalizeAuthor(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val short = if (na.length <= nb.length) na else nb
        val long = if (na.length <= nb.length) nb else na
        return short.length >= 4 && (long.contains(short) || short.contains(long))
    }

    private val lyricsChannels = listOf(
        "keller mx", "vibe music", "latinhype", "lowdrow", "jostland", "latin union",
        "rebel waves", "rap samurai", "sunday", "music lyrics", "un video para ti",
        "letra", "lyrics", "lyric", "traduccion", "sub español"
    )

    fun isLyricsChannel(channel: String?): Boolean {
        val c = normalize(channel)
        return lyricsChannels.any { it in c }
    }

    fun isMusicVideo(video: PipedVideo): Boolean {
        val text = listOfNotNull(video.title, video.uploaderName)
            .joinToString(" ").lowercase()
        val blocked = listOf(
            "gameplay", "gaming", "brookhaven", "minecraft", "roblox", "free fire",
            "fortnite", "gta", "videojuego", "videojuegos", "juegos de", "broma",
            "bromas", "prank", "terror", "comedia", "humor", "reaccion", "vlog"
        )
        return blocked.none { it in text }
    }

    fun musicScore(video: PipedVideo): Int {
        var score = 0
        val title = video.title.orEmpty().lowercase()
        val channel = video.uploaderName.orEmpty()
        if ("vevo" in channel.lowercase() || "topic" in channel.lowercase() ||
            "official video" in title || "video oficial" in title || "audio oficial" in title
        ) score += 2
        if ("letra" in title || "lyrics" in title || "lyric" in title) score -= 2
        if (isLyricsChannel(video.uploaderName)) score -= 2
        return score
    }

    companion object {
        val GENRES = listOf(
            Genre("pop", listOf("pop")),
            Genre("reggaeton", listOf("reggaeton", "perreo", "dembow")),
            Genre("salsa", listOf("salsa", "son cubano", "salseros")),
            Genre("cumbia", listOf("cumbia")),
            Genre("rock", listOf("rock")),
            Genre("bachata", listOf("bachata")),
            Genre("baladas", listOf("balada", "baladas", "romantica")),
            Genre("vallenato", listOf("vallenato")),
            Genre("electronica", listOf("electronica", "edm", "electro", "dance", "house")),
            Genre("corridos", listOf("corrido", "corridos", "banda", "norteño")),
        )
        data class Genre(val key: String, val keywords: List<String>)
    }

    fun genreOf(video: PipedVideo): String? {
        val text = " ${(video.title.orEmpty() + " " + (video.uploaderName.orEmpty())).lowercase()} "
        return GENRES.firstOrNull { (_, kws) -> kws.any { " $it " in text } }?.key
    }

    fun matchesGenre(genre: String?, video: PipedVideo): Boolean {
        if (genre.isNullOrBlank()) return true
        val entry = GENRES.firstOrNull { it.key == genre } ?: return true
        val text = " ${(video.title.orEmpty() + " " + (video.uploaderName.orEmpty())).lowercase()} "
        return entry.keywords.any { " $it " in text }
    }

    /**
     * Busca videos similares a uno dado, filtrando duplicados y contenido no musical.
     */
    suspend fun findSimilarVideos(
        seed: PipedVideo,
        seen: Set<String>,
        genre: String?,
        recentAuthors: List<String>
    ): List<PipedVideo> {
        val videoId = extractVideoId(seed.url.orEmpty())
        val artist = seed.uploaderName?.takeIf { it.isNotBlank() && !it.contains("Topic", true) } ?: ""
        val similar = mutableListOf<PipedVideo>()

        fun collect(results: List<PipedVideo>) {
            for (v in results) {
                val id = extractVideoId(v.url.orEmpty())
                if (id.isEmpty() || id == videoId || id in seen) continue
                if (!isMusicVideo(v)) continue
                if (sameSong(v.title, seed.title)) continue
                if (similar.any { sameSong(it.title, v.title) }) continue
                similar.add(v)
            }
        }

        val baseQuery = listOfNotNull(artist, seed.title).joinToString(" ").take(60)
        try {
            collect(InnerTubeSearch.search(baseQuery.ifBlank { "música" }))
        } catch (_: Exception) {}
        if (similar.isEmpty()) {
            try {
                collect(youTubeManager.searchYouTube(baseQuery.ifBlank { "música" }, musicOnly = true).map { it.toPipedVideo() })
            } catch (_: Exception) {}
        }
        if (similar.isEmpty()) {
            try {
                collect(api.search(baseQuery.ifBlank { "música" }).items)
            } catch (e: Exception) {
                android.util.Log.e("RecommendationEngine", "error similares para $videoId", e)
            }
        }
        if (similar.size < 8 && artist.isNotBlank()) {
            val artistQuery = "$artist música"
            try {
                collect(InnerTubeSearch.search(artistQuery))
            } catch (_: Exception) {}
            if (similar.size < 8) {
                try {
                    collect(youTubeManager.searchYouTube(artistQuery, musicOnly = true).map { it.toPipedVideo() })
                } catch (_: Exception) {}
            }
            if (similar.size < 8) {
                try {
                    collect(api.search(artistQuery).items)
                } catch (e: Exception) {
                    android.util.Log.e("RecommendationEngine", "error similares artista para $videoId", e)
                }
            }
        }
        if (similar.size < 12) {
            // Note: the 'preferred' query would normally come from the Activity/UserPrefs
            // but since we are inside the engine, we handle the generic music fallback
            // if the genre is provided, we can use it here.
        }

        val seenIds = seen + videoId
        return rankCandidates(
            similar, seed, genre, recentAuthors, seenIds, { v -> extractVideoId(v.url.orEmpty()) }
        ).take(15)
    }

    /**
     * Busca videos nuevos basados en una query y el historial para extender la cola.
     */
    suspend fun searchNewVideos(
        query: String,
        seed: PipedVideo?,
        seen: Set<String>,
        genre: String?,
        recentAuthors: List<String>,
        count: Int
    ): List<PipedVideo> {
        val fresh = mutableListOf<PipedVideo>()
        try {
            val results = InnerTubeSearch.search(query)
            fresh.addAll(results.filter { isMusicVideo(it) })
        } catch (_: Exception) {}

        if (fresh.size < count) {
            try {
                val ytVideos = youTubeManager.searchYouTube(query, musicOnly = true)
                fresh.addAll(ytVideos.map { it.toPipedVideo() }.filter { isMusicVideo(it) })
            } catch (_: Exception) {}
        }

        if (fresh.size < count) {
            try {
                val apiResults = api.search(query).items
                fresh.addAll(apiResults.filter { isMusicVideo(it) })
            } catch (_: Exception) {}
        }

        // Filtrar duplicados y canciones ya vistas
        val finalCandidates = fresh.filter {
            val id = extractVideoId(it.url.orEmpty())
            id.isNotEmpty() && id !in seen && !fresh.filter { f -> f != it }.any { f -> sameSong(f.title, it.title) }
        }

        val seenIds = seen + (seed?.let { extractVideoId(it.url.orEmpty()) } ?: "")
        return rankCandidates(
            finalCandidates, seed, genre, recentAuthors, seenIds, { v -> extractVideoId(v.url.orEmpty()) }
        ).take(count)
    }

    fun rankCandidates(
        candidates: List<PipedVideo>,
        seed: PipedVideo?,
        genre: String?,
        recentAuthors: List<String>,
        seen: Set<String>,
        extractId: (PipedVideo) -> String,
        jitter: Boolean = false,
    ): List<PipedVideo> {
        val usedTitles = mutableSetOf<String>()
        val ranked = mutableListOf<Pair<PipedVideo, Int>>()
        for (v in candidates) {
            val id = extractId(v)
            if (id.isEmpty() || id in seen) continue
            val normTitle = normalizeSongTitle(v.title)
            if (normTitle.isNotEmpty() && !usedTitles.add(normTitle)) continue
            var score = musicScore(v)
            if (seed != null && sameAuthor(v.uploaderName, seed.uploaderName)) score -= 60
            else if (recentAuthors.any { sameAuthor(v.uploaderName, it) }) score -= 25
            if (matchesGenre(genre, v)) score += 8
            if (jitter) score += Random.nextInt(3)
            ranked.add(v to score)
        }
        val ordered = ranked.sortedByDescending { it.second }.map { it.first }
        return diverseByAuthor(ordered)
    }

    fun diverseByAuthor(list: List<PipedVideo>): List<PipedVideo> {
        val out = mutableListOf<PipedVideo>()
        val remaining = list.toMutableList()
        var lastAuthor: String? = null
        while (remaining.isNotEmpty()) {
            val idx = remaining.indexOfFirst { !sameAuthor(it.uploaderName, lastAuthor) }
            val chosen = remaining.removeAt(if (idx >= 0) idx else 0)
            out.add(chosen)
            lastAuthor = chosen.uploaderName
        }
        return out
    }

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            url.length == 11 -> url
            else -> url.takeLast(11)
        }
    }
}
