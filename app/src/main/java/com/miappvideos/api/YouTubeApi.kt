package com.miappvideos.api

import com.miappvideos.model.YouTubeChannelResponse
import com.miappvideos.model.YouTubePlaylistResponse
import com.miappvideos.model.YouTubeSubscriptionResponse
import com.miappvideos.model.YouTubeVideoResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {

    @GET("subscriptions")
    suspend fun getSubscriptions(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 25
    ): YouTubeSubscriptionResponse

    @GET("playlists")
    suspend fun getPlaylists(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 25
    ): YouTubePlaylistResponse

    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 25
    ): YouTubeVideoResponse

    @GET("videos")
    suspend fun getVideos(
        @Query("part") part: String = "snippet",
        @Query("chart") chart: String = "mostPopular",
        @Query("regionCode") regionCode: String = "MX",
        @Query("maxResults") maxResults: Int = 20
    ): YouTubeVideoResponse

    @GET("channels")
    suspend fun getChannel(
        @Query("part") part: String = "snippet",
        @Query("id") channelId: String
    ): YouTubeChannelResponse

    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String? = null,
        @Query("channelId") channelId: String? = null,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20
    ): YouTubeVideoResponse

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"

        fun create(apiKey: String, accessToken: String?): YouTubeApi {
            val client = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val url = original.url.newBuilder()
                        .addQueryParameter("key", apiKey)
                        .build()
                    val request = if (accessToken != null) {
                        original.newBuilder()
                            .url(url)
                            .header("Authorization", "Bearer $accessToken")
                            .build()
                    } else {
                        original.newBuilder()
                            .url(url)
                            .build()
                    }
                    chain.proceed(request)
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YouTubeApi::class.java)
        }
    }
}
