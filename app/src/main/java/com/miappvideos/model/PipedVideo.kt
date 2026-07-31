package com.miappvideos.model

data class PipedSearchResponse(
    val items: List<PipedVideo>,
    val nextpage: String?
)

data class PipedVideo(
    val url: String?,
    val title: String,
    val thumbnail: String?,
    val uploaderName: String?,
    val uploaderAvatar: String?,
    val uploadedDate: String?,
    val shortDescription: String?,
    val duration: Long?,
    val views: Long?,
    val uploaderVerified: Boolean?,
    val channelId: String? = null
)

data class PipedStreamResponse(
    val title: String?,
    val description: String?,
    val uploadDate: String?,
    val uploaderUrl: String?,
    val uploaderName: String?,
    val uploaderAvatar: String?,
    val thumbnailUrl: String?,
    val duration: Long?,
    val views: Long?,
    val liked: Int?,
    val dislikes: Int?,
    val audioStreams: List<AudioStream>?,
    val videoStreams: List<VideoStream>?
)

data class AudioStream(
    val url: String?,
    val format: String?,
    val quality: String?,
    val mimeType: String?,
    val codec: String?,
    val bitrate: Int?,
    val initStart: Int?,
    val initEnd: Int?,
    val indexStart: Int?,
    val indexEnd: Int?
)

data class VideoStream(
    val url: String?,
    val format: String?,
    val quality: String?,
    val mimeType: String?,
    val codec: String?,
    val videoOnly: Boolean?,
    val initStart: Int?,
    val initEnd: Int?,
    val indexStart: Int?,
    val indexEnd: Int?,
    val bitrate: Int?,
    val width: Int?,
    val height: Int?,
    val fps: Int?
)
