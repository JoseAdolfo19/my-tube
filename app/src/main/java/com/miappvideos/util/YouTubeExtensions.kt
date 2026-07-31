package com.miappvideos.util

import com.miappvideos.model.PipedVideo
import com.miappvideos.model.YouTubeVideo

fun com.miappvideos.model.YouTubeVideo.toPipedVideo(): PipedVideo {
    val videoId = when {
        id?.isJsonObject == true -> id.asJsonObject.get("videoId")?.asString
        id?.isJsonPrimitive == true && id.asString.length == 11 -> id.asString
        else -> null
    } ?: snippet?.resourceId?.videoId ?: snippet?.title?.hashCode()?.toString() ?: ""
    val thumb = snippet?.thumbnails?.medium?.url ?: snippet?.thumbnails?.high?.url
    return PipedVideo(
        url = "https://www.youtube.com/watch?v=$videoId",
        title = snippet?.title ?: "Sin título",
        thumbnail = thumb,
        uploaderName = snippet?.channelTitle,
        uploaderAvatar = null,
        uploadedDate = snippet?.publishedAt,
        shortDescription = null,
        duration = null,
        views = null,
        uploaderVerified = null,
        channelId = snippet?.channelId
    )
}
