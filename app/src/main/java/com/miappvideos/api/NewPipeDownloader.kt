package com.miappvideos.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: NPRequest): NPResponse {
        val okRequest = buildOkRequest(request)
        val response = client.newCall(okRequest).execute()
        return toNPResponse(response)
    }

    private fun buildOkRequest(request: NPRequest): Request {
        val builder = Request.Builder().url(request.url())
        val headers = request.headers() ?: emptyMap()
        for ((key, values) in headers) {
            values.forEach { builder.header(key, it) }
        }
        val userAgent = headers["User-Agent"]?.firstOrNull()
            ?: "Mozilla/5.0 (Linux; Android 14) NewPipeExtractor"
        builder.header("User-Agent", userAgent)

        val data = request.dataToSend()
        val bodyString = data?.let { String(it) } ?: ""
        when (request.httpMethod()) {
            "POST" -> builder.post(bodyString.toRequestBody(null))
            "HEAD" -> builder.head()
            "PATCH" -> builder.patch(bodyString.toRequestBody(null))
            "DELETE" -> builder.delete(bodyString.toRequestBody(null))
            "PUT" -> builder.put(bodyString.toRequestBody(null))
            else -> builder.get()
        }
        return builder.build()
    }

    private fun toNPResponse(response: Response): NPResponse {
        val body = response.body?.string() ?: ""
        val headers = mutableMapOf<String, MutableList<String>>()
        response.headers.forEach { header ->
            headers.getOrPut(header.first) { mutableListOf() }.add(header.second)
        }
        return NPResponse(
            response.code,
            response.message,
            headers,
            body,
            response.request.url.toString()
        )
    }
}
