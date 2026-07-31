package com.miappvideos.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

class MyMediaNotificationProvider(context: Context) :
    DefaultMediaNotificationProvider(
        context,
        { 1001 },
        CHANNEL_ID,
        com.miappvideos.R.string.channel_name
    ) {

    private val appContext: Context = context.applicationContext

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        return ImmutableList.Builder<CommandButton>()
            .add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .setIconResId(androidx.media3.session.R.drawable.media3_notification_seek_to_previous)
                    .setDisplayName(
                        appContext.getString(
                            androidx.media3.session.R.string.media3_controls_seek_to_previous_description
                        )
                    )
                    .build()
            )
            .add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setIconResId(
                        if (showPauseButton) {
                            androidx.media3.session.R.drawable.media3_notification_pause
                        } else {
                            androidx.media3.session.R.drawable.media3_notification_play
                        }
                    )
                    .setDisplayName(
                        appContext.getString(
                            if (showPauseButton) {
                                androidx.media3.session.R.string.media3_controls_pause_description
                            } else {
                                androidx.media3.session.R.string.media3_controls_play_description
                            }
                        )
                    )
                    .build()
            )
            .add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setIconResId(androidx.media3.session.R.drawable.media3_notification_seek_to_next)
                    .setDisplayName(
                        appContext.getString(
                            androidx.media3.session.R.string.media3_controls_seek_to_next_description
                        )
                    )
                    .build()
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "background_playback"
    }
}
