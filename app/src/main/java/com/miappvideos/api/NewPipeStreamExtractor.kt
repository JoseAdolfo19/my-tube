package com.miappvideos.api

import android.util.Log
import com.miappvideos.model.AudioStream
import com.miappvideos.model.PipedStreamResponse
import com.miappvideos.model.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo

object NewPipeStreamExtractor {

    private const val TAG = "NewPipeStreamExtractor"

    suspend fun getStreams(videoId: String): PipedStreamResponse? = withContext(Dispatchers.IO) {
        try {
            val service = NewPipe.getService("YouTube")
            Log.d(TAG, "Service loaded: ${service.serviceInfo.name}")
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            Log.d(TAG, "Extractor created, fetching page...")
            val info = StreamInfo.getInfo(extractor)
            Log.d(TAG, "StreamInfo obtained: ${info.name}")

            info.errors.forEach { err ->
                Log.w(TAG, "StreamInfo error: ${err.message}", err)
            }

            val audioStreams = info.audioStreams.orEmpty().mapNotNull { audio ->
                val url = audio.content
                val codec = audio.codec
                val mimeType = audio.format?.mimeType?.let {
                    if (codec != null) "$it; codecs=\"$codec\"" else it
                } ?: "audio/mp4"
                AudioStream(
                    url = url,
                    format = audio.format?.name,
                    quality = if (audio.averageBitrate > 0) "${audio.averageBitrate / 1000}kbps" else null,
                    mimeType = mimeType,
                    codec = codec,
                    bitrate = audio.averageBitrate.takeIf { it > 0 },
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null
                )
            }
            Log.d(TAG, "Audio streams extracted: ${audioStreams.size}")

            val videoStreams = mutableListOf<VideoStream>()

            info.videoStreams.orEmpty().forEach { video ->
                videoStreams.add(VideoStream(
                    url = video.content,
                    format = video.format?.name,
                    quality = video.quality,
                    mimeType = video.format?.mimeType ?: "video/mp4",
                    codec = video.codec,
                    videoOnly = video.isVideoOnly(),
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                    bitrate = video.bitrate.takeIf { it > 0 },
                    width = null, height = null, fps = null
                ))
            }
            Log.d(TAG, "Video streams extracted: ${videoStreams.size}")

            info.videoOnlyStreams.orEmpty().forEach { video ->
                videoStreams.add(VideoStream(
                    url = video.content,
                    format = video.format?.name,
                    quality = video.quality,
                    mimeType = video.format?.mimeType ?: "video/mp4",
                    codec = video.codec,
                    videoOnly = true,
                    initStart = null, initEnd = null, indexStart = null, indexEnd = null,
                    bitrate = video.bitrate.takeIf { it > 0 },
                    width = null, height = null, fps = null
                ))
            }
            Log.d(TAG, "Total video streams (incl. video-only): ${videoStreams.size}")

            return@withContext PipedStreamResponse(
                title = info.name,
                description = null, uploadDate = null, uploaderUrl = null,
                uploaderName = info.uploaderName,
                uploaderAvatar = null, thumbnailUrl = null, duration = info.duration,
                views = info.viewCount, liked = null, dislikes = null,
                audioStreams = audioStreams, videoStreams = videoStreams
            )
        } catch (e: Exception) {
            Log.e(TAG, "getStreams failed for videoId=$videoId", e)
            null
        }
    }
}
