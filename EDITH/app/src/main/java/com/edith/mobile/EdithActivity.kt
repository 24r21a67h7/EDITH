package com.edith.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.edith.R

/**
 * EDITH's main (and only) Activity for Mission 01.
 *
 * Provides:
 * - Runtime permission handling (microphone, notifications)
 * - "Activate EDITH" button (push-to-talk)
 * - Status and response display
 * - Start/stop for the foreground service
 *
 * The UI is intentionally minimal. EDITH is voice-first.
 * This Activity is primarily for permissions, diagnostics, and the
 * initial activation trigger (since the foreground service must be
 * started from a visible Activity on Android 14+).
 */
class EdithActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private lateinit var activateButton: Button
    private lateinit var serviceButton: Button

    private var isServiceRunning = false

    companion object {
        private const val TAG = "EDITH.Activity"
    }

    // Permission request launchers
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Microphone permission granted")
            checkAndRequestNotificationPermission()
        } else {
            Log.w(TAG, "Microphone permission denied")
            statusText.text = "Microphone permission is required for EDITH to hear you."
        }
        updateUiState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied — service notification may not show")
        }
        updateUiState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edith)

        statusText = findViewById(R.id.statusText)
        responseText = findViewById(R.id.responseText)
        activateButton = findViewById(R.id.activateButton)
        serviceButton = findViewById(R.id.serviceButton)

        activateButton.setOnClickListener { onActivateClicked() }
        serviceButton.setOnClickListener { onServiceButtonClicked() }

        // Request permissions on first launch
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
    }

    private fun checkAndRequestPermissions() {
        if (!hasMicPermission()) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            checkAndRequestNotificationPermission()
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun onServiceButtonClicked() {
        if (!isServiceRunning) {
            startEdithService()
        } else {
            stopEdithService()
        }
    }

    private fun startEdithService() {
        if (!hasMicPermission()) {
            statusText.text = "Cannot start EDITH without microphone permission."
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val intent = Intent(this, EdithForegroundService::class.java).apply {
            action = EdithForegroundService.ACTION_START
        }
        startForegroundService(intent)
        isServiceRunning = true
        updateUiState()
        Log.i(TAG, "EDITH service started")
    }

    private fun stopEdithService() {
        val intent = Intent(this, EdithForegroundService::class.java).apply {
            action = EdithForegroundService.ACTION_STOP
        }
        startService(intent)
        isServiceRunning = false
        updateUiState()
        Log.i(TAG, "EDITH service stopped")
    }

    private fun onActivateClicked() {
        if (!isServiceRunning) {
            startEdithService()
            // Give the service a moment to start, then activate
            activateButton.postDelayed({
                sendActivationToService()
            }, 1000)
        } else {
            sendActivationToService()
        }
    }

    private fun sendActivationToService() {
        val intent = Intent(this, EdithForegroundService::class.java).apply {
            action = NotificationHelper.ACTION_ACTIVATE
        }
        startService(intent)
        statusText.text = "Listening..."
    }

    private fun updateUiState() {
        val hasMic = hasMicPermission()
        val hasNotif = hasNotificationPermission()

        activateButton.isEnabled = hasMic
        serviceButton.text = if (isServiceRunning) "Stop EDITH" else "Start EDITH"

        if (!hasMic) {
            statusText.text = "Microphone permission required"
            activateButton.isEnabled = false
        } else if (isServiceRunning) {
            statusText.text = "EDITH is active — tap Activate to speak"
        } else {
            statusText.text = "EDITH is offline — tap Start to begin"
        }

        val edithCore = EdithApplication.getInstance().edithCore
        responseText.text = if (!isServiceRunning) {
            edithCore.identity.assistantName + " v0.1 — Mission 01"
        } else {
            ""
        }
    }
}
