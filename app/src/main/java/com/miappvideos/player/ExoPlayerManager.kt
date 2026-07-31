package com.miappvideos.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerManager(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .build()

    var isPlaying: Boolean
        get() = player.isPlaying
        set(value) {
            if (value) player.play() else player.pause()
        }

    var currentVideoId: String? = null
    var currentTitle: String = ""
    var currentThumbnail: String? = null

    private val listeners = mutableListOf<Player.Listener>()

    fun playUrl(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
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
