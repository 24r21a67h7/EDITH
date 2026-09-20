package com.edith.mobile

import android.app.Application
import android.util.Log
import com.edith.core.EdithCore

/**
 * EDITH Application class.
 *
 * Initializes the notification channel on startup and holds the
 * singleton [EdithCore] instance shared across the app.
 */
class EdithApplication : Application() {

    lateinit var edithCore: EdithCore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize EDITH Core
        edithCore = EdithCore()
        Log.i(TAG, edithCore.greet())

        // Create notification channel for the foreground service
        NotificationHelper.createNotificationChannel(this)
    }

    companion object {
        private const val TAG = "EDITH"

        @Volatile
        private lateinit var instance: EdithApplication

        fun getInstance(): EdithApplication = instance
    }
}
