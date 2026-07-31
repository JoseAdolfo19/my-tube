package com.miappvideos.api

import com.miappvideos.model.PipedSearchResponse
import com.miappvideos.model.PipedStreamResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

interface PipedApi {

    @GET("search")
    suspend fun search(@Query("q") query: String): PipedSearchResponse

    @GET("streams/{videoId}")
    suspend fun getStreams(@Path("videoId") videoId: String): PipedStreamResponse

    @GET("trending")
    suspend fun trending(): PipedSearchResponse

    companion object {
        private const val BASE_URL = "https://pipedapi.kavin.rocks/"

        fun create(): PipedApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PipedApi::class.java)
        }
    }
}
