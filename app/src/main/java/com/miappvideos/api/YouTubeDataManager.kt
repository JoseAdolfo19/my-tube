package com.miappvideos.api

import android.content.Context
import com.miappvideos.BuildConfig
import com.miappvideos.model.YouTubeSubscription
import com.miappvideos.model.YouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeDataManager(private val context: Context) {

    private var accessToken: String? = null

    private val apiKey: String
        get() = BuildConfig.YOUTUBE_API_KEY

    fun setAccessToken(token: String?) {
        accessToken = token
    }

    private fun createApi() = YouTubeApi.create(apiKey, accessToken)

    suspend fun getSubscriptions(): List<YouTubeSubscription> = withContext(Dispatchers.IO) {
        try {
            createApi().getSubscriptions().items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPlaylists(): List<com.miappvideos.model.YouTubePlaylist> = withContext(Dispatchers.IO) {
        try {
            createApi().getPlaylists().items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChannelUploadsVideos(channelId: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val channel = createApi().getChannel(part = "contentDetails", channelId = channelId)
                .items?.firstOrNull()
            val uploadsId = channel?.contentDetails?.relatedPlaylists?.uploads ?: return@withContext emptyList()
            createApi().getPlaylistItems(playlistId = uploadsId, maxResults = 10).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularVideos(region: String = "MX"): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            createApi().getVideos(regionCode = region).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchYouTube(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            createApi().search(query = query).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchYouTubeByChannel(channelId: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            createApi().search(query = null, channelId = channelId).items ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun refreshToken(accountName: String?) {
        if (accountName == null) {
            accessToken = null
            return
        }
        try {
            val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                context,
                accountName,
                "oauth2:https://www.googleapis.com/auth/youtube.readonly"
            )
            accessToken = token
        } catch (e: Exception) {
            accessToken = null
        }
    }
}
