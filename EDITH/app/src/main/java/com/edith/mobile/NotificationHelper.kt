package com.edith.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.edith.R
import com.edith.runtime.EdithState

/**
 * Manages EDITH's notification channel and foreground service notification.
 *
 * Privacy: the notification shows only a coarse status word (Standby / Listening /
 * Processing / Speaking / Setup needed). It never contains recognized speech or
 * EDITH's answers, and it is hidden from the lock screen.
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
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /** The minimum status text for the notification. */
    fun statusText(context: Context, state: EdithState, needsSetup: Boolean): String = when {
        state == EdithState.STANDBY && needsSetup -> context.getString(R.string.status_setup_needed)
        state == EdithState.STANDBY -> context.getString(R.string.status_standby)
        state == EdithState.LISTENING -> context.getString(R.string.status_listening)
        state == EdithState.PROCESSING -> context.getString(R.string.status_processing)
        else -> context.getString(R.string.status_speaking)
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

        // "Activate" action → triggers voice input (ignored unless EDITH is idle)
        val activateIntent = Intent(context, EdithForegroundService::class.java).apply {
            action = ACTION_ACTIVATE
        }
        val activatePending = PendingIntent.getService(
            context, 1, activateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_edith_notification)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(
                R.drawable.ic_edith_notification,
                context.getString(R.string.notification_action_activate),
                activatePending
            )
            .build()
    }
}
