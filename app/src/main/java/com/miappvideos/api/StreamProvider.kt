package com.miappvideos.api

import com.miappvideos.model.PipedStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StreamProvider {

    private val cache = mutableMapOf<String, PipedStreamResponse?>()

    suspend fun getStreams(videoId: String): PipedStreamResponse? = withContext(Dispatchers.IO) {
        cache[videoId] ?: run {
            val result = NewPipeStreamExtractor.getStreams(videoId)
            cache[videoId] = result
            result
        }
    }

    suspend fun preload(vararg videoIds: String) {
        for (id in videoIds) {
            if (id !in cache) {
                val result = NewPipeStreamExtractor.getStreams(id)
                cache[id] = result
            }
        }
    }

    fun clearCache() {
        cache.clear()
    }
}