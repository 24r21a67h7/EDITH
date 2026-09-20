package com.edith.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.edith.R

/**
 * Manages EDITH's notification channel and foreground service notification.
 */
object NotificationHelper {

    const val CHANNEL_ID = "edith_service_channel"
    const val NOTIFICATION_ID = 1
    const val ACTION_ACTIVATE = "com.edith.ACTION_ACTIVATE"

    /**
     * Creates the notification channel for EDITH's foreground service.
     * Must be called before starting the foreground service (typically in Application.onCreate).
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EDITH Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "EDITH assistant background service"
            setShowBadge(false)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the persistent foreground notification for EDITH.
     *
     * Includes an "Activate" action button for push-to-talk.
     */
    fun buildForegroundNotification(context: Context, statusText: String): Notification {
        // Tap notification → open EdithActivity
        val openIntent = Intent(context, EdithActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Activate" action → triggers voice input
        val activateIntent = Intent(context, EdithForegroundService::class.java).apply {
            action = ACTION_ACTIVATE
        }
        val activatePending = PendingIntent.getService(
            context, 1, activateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("EDITH")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_edith_notification)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_edith_notification,
                "Activate",
                activatePending
            )
            .build()
    }
}
