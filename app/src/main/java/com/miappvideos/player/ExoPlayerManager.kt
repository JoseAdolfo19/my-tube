package com.miappvideos.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.miappvideos.api.innertube.RotatingHttpClient

class ExoPlayerManager(context: Context) {

    private val dataSourceFactory = object : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RangeFixingDataSource(
                OkHttpDataSource.Factory(RotatingHttpClient.client()).createDataSource()
            )
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .setMediaSourceFactory(
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
        )
        .build()

    var isPlaying: Boolean
        get() = player.isPlaying
        set(value) {
            if (value) player.play() else player.pause()
        }

    var currentVideoId: String? = null
    var currentTitle: String = ""
    var currentThumbnail: String? = null

    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null

    private val listeners = mutableListOf<Player.Listener>()

    fun playUrl(url: String, title: String = "") {
        playAudioVideo(url, null, title)
    }

    fun playAudioVideo(
        audioUrl: String,
        videoUrl: String?,
        title: String = "",
        startPositionMs: Long = 0L,
    ) {
        // Detener y liberar codecs viejos antes de cargar nuevos, para evitar
        // que el resource manager reclame los codecs durante la transicion
        // (problema comun en emuladores y dispositivos con codecs software como opus).
        player.stop()
        player.clearMediaItems()

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist("MyTube")
            .build()
        val source = if (videoUrl != null) {
            MergingMediaSource(
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.Builder().setUri(audioUrl).build()),
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.Builder().setUri(videoUrl).build())
            )
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.Builder().setUri(audioUrl).setMediaMetadata(metadata).build())
        }
        player.setMediaSource(source)
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.play()
    }

    fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        player.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        player.removeListener(listener)
    }

    fun release() {
        player.release()
    }

    fun getAudioUrl(audioStreams: List<com.miappvideos.model.AudioStream>?): String? {
        if (audioStreams.isNullOrEmpty()) return null
        return audioStreams.maxByOrNull { it.bitrate ?: 0 }?.url
    }

    fun getVideoUrl(videoStreams: List<com.miappvideos.model.VideoStream>?): String? {
        if (videoStreams.isNullOrEmpty()) return null
        val filtered = videoStreams.filter { it.videoOnly == false }
        return filtered.maxByOrNull { it.height ?: 0 }?.url
            ?: videoStreams.maxByOrNull { it.height ?: 0 }?.url
    }
}
