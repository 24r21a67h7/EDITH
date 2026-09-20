package com.edith.mobile

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.edith.core.EdithCore

/**
 * EDITH Foreground Service.
 *
 * Hosts the EDITH runtime loop:
 *   Activation → Voice Input → Core Processing → Voice Output → Standby
 *
 * Runs as a foreground service with FOREGROUND_SERVICE_TYPE_MICROPHONE
 * so it can access the microphone while the user is in other apps.
 *
 * Important Android constraints:
 * - Must be started while the app has a visible Activity (Android 14+ restriction)
 * - RECORD_AUDIO permission must be granted before starting
 * - Shows a persistent notification with an "Activate" action
 */
class EdithForegroundService : Service() {

    private lateinit var edithCore: EdithCore
    private lateinit var voiceInput: AndroidVoiceInput
    private lateinit var voiceOutput: AndroidVoiceOutput
    private lateinit var activationDetector: ManualActivationDetector
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Current state of the EDITH runtime. */
    enum class State {
        STANDBY,
        LISTENING,
        PROCESSING,
        SPEAKING
    }

    @Volatile
    var currentState: State = State.STANDBY
        private set

    companion object {
        private const val TAG = "EDITH.Service"
        const val ACTION_START = "com.edith.ACTION_START"
        const val ACTION_STOP = "com.edith.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "EDITH Service created")

        edithCore = EdithApplication.getInstance().edithCore
        voiceInput = AndroidVoiceInput(this)
        voiceOutput = AndroidVoiceOutput(this)
        activationDetector = ManualActivationDetector()

        // Initialize TTS
        voiceOutput.initialize {
            Log.i(TAG, "Voice output ready")
        }

        // Set up activation detector
        activationDetector.startDetecting {
            onActivated()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop requested")
                stopSelf()
                return START_NOT_STICKY
            }
            NotificationHelper.ACTION_ACTIVATE -> {
                Log.i(TAG, "Activation triggered via notification")
                activationDetector.triggerActivation()
            }
            else -> {
                // Start foreground with microphone type
                val notification = NotificationHelper.buildForegroundNotification(
                    this, "Standby — Ready for activation"
                )
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
                Log.i(TAG, "EDITH Service started in foreground")

                // Speak greeting
                mainHandler.postDelayed({
                    if (voiceOutput.isAvailable()) {
                        voiceOutput.speak(edithCore.greet())
                    }
                }, 500)
            }
        }

        return START_STICKY
    }

    /**
     * Called when EDITH is activated (via button or notification action).
     * Starts the runtime loop: listen → process → speak → standby.
     */
    private fun onActivated() {
        if (currentState != State.STANDBY) {
            Log.w(TAG, "Activation ignored — EDITH is busy (state: $currentState)")
            return
        }

        Log.i(TAG, "EDITH activated — starting listening")
        currentState = State.LISTENING
        updateNotification("Listening...")

        // Must run on main thread (SpeechRecognizer requirement)
        mainHandler.post {
            voiceInput.startListening(
                onResult = { transcribedText ->
                    onVoiceInputResult(transcribedText)
                },
                onError = { error ->
                    Log.w(TAG, "Voice input error: $error")
                    currentState = State.STANDBY
                    updateNotification("Standby — Ready for activation")
                }
            )
        }
    }

    /**
     * Called when speech recognition produces a result.
     */
    private fun onVoiceInputResult(text: String) {
        Log.i(TAG, "Heard: \"$text\"")
        currentState = State.PROCESSING
        updateNotification("Processing: \"$text\"")

        // Process through EDITH Core
        val result = edithCore.processCommand(text)
        Log.i(TAG, "Response: ${result.spokenResponse}")

        // Speak the response
        currentState = State.SPEAKING
        updateNotification("Speaking...")

        voiceOutput.speak(result.spokenResponse) {
            // Return to standby after speaking
            currentState = State.STANDBY
            updateNotification("Standby — Ready for activation")
            Log.i(TAG, "Returned to standby")
        }
    }

    /**
     * Updates the foreground notification with current status.
     */
    private fun updateNotification(statusText: String) {
        val notification = NotificationHelper.buildForegroundNotification(this, statusText)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "EDITH Service destroyed")
        activationDetector.destroy()
        voiceInput.destroy()
        voiceOutput.destroy()
        super.onDestroy()
    }
}
