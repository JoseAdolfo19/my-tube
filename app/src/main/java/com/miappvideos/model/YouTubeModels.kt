package com.miappvideos.model

// --- Subscriptions ---
data class YouTubeSubscriptionResponse(
    val items: List<YouTubeSubscription>?,
    val nextPageToken: String?,
    val pageInfo: PageInfo?
)

data class YouTubeSubscription(
    val id: String?,
    val snippet: SubscriptionSnippet?
)

data class SubscriptionSnippet(
    val title: String?,
    val description: String?,
    val resourceId: ResourceId?,
    val thumbnails: Thumbnails?,
    val channelId: String?
)

data class ResourceId(
    val channelId: String?
)

// --- Playlists ---
data class YouTubePlaylistResponse(
    val items: List<YouTubePlaylist>?,
    val nextPageToken: String?,
    val pageInfo: PageInfo?
)

data class YouTubePlaylist(
    val id: String?,
    val snippet: PlaylistSnippet?
)

data class PlaylistSnippet(
    val title: String?,
    val description: String?,
    val thumbnails: Thumbnails?,
    val channelId: String?,
    val channelTitle: String?
)

// --- Videos (search results, playlist items, popular) ---
data class YouTubeVideoResponse(
    val items: List<YouTubeVideo>?,
    val nextPageToken: String?,
    val pageInfo: PageInfo?
)

data class YouTubeVideo(
    val id: com.google.gson.JsonElement?,
    val snippet: VideoSnippet?
)

data class VideoSnippet(
    val title: String?,
    val description: String?,
    val thumbnails: Thumbnails?,
    val channelId: String?,
    val channelTitle: String?,
    val publishedAt: String?,
    val resourceId: VideoResourceId?
)

data class VideoResourceId(
    val videoId: String?
)

// --- Channel ---
data class YouTubeChannelResponse(
    val items: List<YouTubeChannel>?,
    val pageInfo: PageInfo?
)

data class YouTubeChannel(
    val id: String?,
    val snippet: ChannelSnippet?,
    val statistics: ChannelStatistics?,
    val contentDetails: ChannelContentDetails?
)

data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists?
)

data class RelatedPlaylists(
    val uploads: String?,
    val favorites: String?,
    val watchHistory: String?,
    val watchLater: String?
)

data class ChannelSnippet(
    val title: String?,
    val description: String?,
    val thumbnails: Thumbnails?,
    val customUrl: String?
)

data class ChannelStatistics(
    val subscriberCount: String?,
    val videoCount: String?,
    val viewCount: String?
)

// --- Shared ---
data class Thumbnails(
    val default: ThumbnailInfo?,
    val medium: ThumbnailInfo?,
    val high: ThumbnailInfo?
)

data class ThumbnailInfo(
    val url: String?,
    val width: Int?,
    val height: Int?
)

data class PageInfo(
    val totalResults: Int?,
    val resultsPerPage: Int?
)
