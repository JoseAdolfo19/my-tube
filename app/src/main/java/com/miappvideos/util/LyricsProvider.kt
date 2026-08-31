package com.miappvideos.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Proveedor de letras via lrclib.net (gratuito, sin API key).
 * Busca por titulo+artista y como respaldo solo por titulo.
 * Si no hay letra devuelve null; si la cancion es instrumental lo indica.
 */
object LyricsProvider {

    data class Lyrics(
        val text: String,
        val instrumental: Boolean,
        val trackName: String,
        val artistName: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val API = "https://lrclib.net/api/search"

    suspend fun fetch(title: String, artist: String?): Lyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitle(title)
        if (cleanTitle.isBlank()) return@withContext null

        val queries = mutableListOf<String>()
        val cleanArtist = artist?.let { cleanArtist(it) }
        if (!cleanArtist.isNullOrBlank()) queries += "$cleanTitle $cleanArtist"
        queries += cleanTitle

        for (q in queries) {
            val result = search(q)
            if (result != null) return@withContext result
        }
        null
    }

    private fun search(query: String): Lyrics? {
        return try {
            val url = "$API?q=${URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyTube/2.1.0 (https://mytubemusic.vercel.app)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                pickBest(JSONArray(body))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun pickBest(arr: JSONArray): Lyrics? {
        var firstSynced: JSONArray? = null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val instrumental = item.optBoolean("instrumental", false)

            val plain = item.optString("plainLyrics", "").trim()
            if (plain.isNotEmpty() && plain != "null") {
                return Lyrics(
                    text = plain,
                    instrumental = instrumental,
                    trackName = item.optString("trackName", ""),
                    artistName = item.optString("artistName", ""),
                )
            }

            val synced = item.optString("syncedLyrics", "").trim()
            if (synced.isNotEmpty() && synced != "null" && firstSynced == null) {
                firstSynced = JSONArray().put(item)
            }
        }

        // Respaldo: usar sincronizada quitando las etiquetas de tiempo
        firstSynced?.getJSONObject(0)?.let { item ->
            val synced = item.optString("syncedLyrics", "").trim()
            val plain = stripTimestamps(synced)
            if (plain.isNotBlank()) {
                return Lyrics(
                    text = plain,
                    instrumental = item.optBoolean("instrumental", false),
                    trackName = item.optString("trackName", ""),
                    artistName = item.optString("artistName", ""),
                )
            }
        }
        return null
    }

    /** Convierte LRC "[00:12.34] texto" a texto plano. */
    private fun stripTimestamps(synced: String): String {
        return synced.lines()
            .map { it.replace(Regex("^\\[[^]]*]\\s*"), "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** Limpia el titulo: quita (Official Video), [HD], feat., etc. */
    private fun cleanTitle(title: String): String {
        var t = title
        t = t.replace(Regex("\\([^)]*\\)|\\[[^\\]]*]"), " ")
        t = t.replace(
            Regex(
                "\\b(official|video|oficial|letra|lyrics|lyric|audio|hd|4k|mv|m/v|visualizer|en vivo|live|remasterizad\\w*|feat\\.?|ft\\.?)\\b",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        t = t.replace(Regex("[-–—|]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    /** Limpia el artista: quita VEVO, Official, - Topic, etc. */
    private fun cleanArtist(artist: String): String {
        var a = artist
        a = a.replace(
            Regex("\\b(VEVO|Official|Topic|Records|TV)\\b", RegexOption.IGNORE_CASE),
            " "
        )
        a = a.replace(Regex("[-–—|]"), " ")
        a = a.replace(Regex("\\s+"), " ").trim()
        return a
    }
}
