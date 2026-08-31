package com.miappvideos.ui.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.miappvideos.R

object UpdateNotification {
    private const val CHANNEL_ID = "update_channel"
    private const val NOTIFICATION_ID = 1001

    fun showUpdateNotification(context: Context, versionName: String, releaseNotes: List<String>) {
        createChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManagerCompat

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nueva versión disponible")
            .setContentText("MY-TUBE $versionName")
            .setStyle(NotificationCompat.BigTextStyle().bigText(releaseNotes.joinToString("\n• ")))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showNoUpdateNotification(context: Context) {
        createChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManagerCompat

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Sin actualizaciones")
            .setContentText("MY-TUBE está actualizado")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de actualización"
                setShowBadge(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
