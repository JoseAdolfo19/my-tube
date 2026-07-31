package com.miappvideos.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.miappvideos.MainActivity
import com.miappvideos.R

class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        ExoPlayerHolder.ensure(applicationContext)
        setMediaNotificationProvider(MyMediaNotificationProvider(this))

        mediaSession = MediaSession.Builder(this, ExoPlayerHolder.player.player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = Player.Commands.Builder()
                        .addAllCommands()
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(commands)
                        .build()
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    if (playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                        Log.d(TAG, "player command: NEXT")
                        ExoPlayerHolder.player.onNext?.invoke()
                    } else if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                        Log.d(TAG, "player command: PREVIOUS")
                        ExoPlayerHolder.player.onPrevious?.invoke()
                    }
                    return super.onPlayerCommandRequest(session, controllerInfo, playerCommand)
                }
            })
            .build()
        addSession(mediaSession!!)
        Log.d(TAG, "session added: ${getSessions().size}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keyCode = intent
            ?.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
            ?.keyCode
        Log.d(TAG, "onStartCommand action=${intent?.action} keyCode=$keyCode")
        super.onStartCommand(intent, flags, startId)

        val videoId = intent?.getStringExtra("video_id")
        val title = intent?.getStringExtra("title") ?: "Reproduciendo"

        if (videoId != null) {
            ExoPlayerHolder.player.currentVideoId = videoId
            ExoPlayerHolder.player.currentTitle = title
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "onGetSession pkg=${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        ExoPlayerHolder.player.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "PlayerService"
        private const val CHANNEL_ID = "background_playback"
    }
}

object ExoPlayerHolder {
    lateinit var player: ExoPlayerManager

    fun ensure(context: Context) {
        if (!::player.isInitialized) {
            player = ExoPlayerManager(context)
        }
    }
}
