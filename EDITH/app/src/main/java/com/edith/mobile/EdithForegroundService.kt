package com.edith.mobile

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.edith.runtime.EdithState
import com.edith.runtime.InteractionLoop
import com.edith.runtime.LoopListener
import com.edith.voice.VoiceError
import com.edith.voice.VoiceOutputStatus

/**
 * EDITH Foreground Service. Hosts the [InteractionLoop]:
 *   Activation → Voice Input → Core Processing → Voice Output → Standby
 *
 * Lifecycle rules (Android 14+ microphone FGS restrictions, Android 17 background-audio
 * hardening):
 * - The service is only ever promoted to a microphone foreground service in response to
 *   an explicit [ACTION_START] / [ACTION_START_AND_ACTIVATE] sent while the app is visible.
 * - It is NOT sticky. If the system kills it, it stays dead: a restart would arrive with a
 *   null intent, in the background, where a microphone foreground service may not be started
 *   (and where audio calls would silently fail on Android 17). Any null/unknown intent is
 *   ignored without touching the microphone.
 * - If `startForeground` is refused, the service logs, records the problem and stops itself
 *   (no crash loop).
 * - Activation requests are only honored while the runtime exists and is in STANDBY.
 *
 * Privacy: transcripts are never logged and never placed in the notification.
 */
class EdithForegroundService : Service(), LoopListener {

    private var loop: InteractionLoop? = null
    private var voiceInput: AndroidVoiceInput? = null
    private var voiceOutput: AndroidVoiceOutput? = null
    private var activationDetector: ManualActivationDetector? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "EDITH.Service"
        const val ACTION_START = "com.edith.ACTION_START"
        const val ACTION_START_AND_ACTIVATE = "com.edith.ACTION_START_AND_ACTIVATE"
        const val ACTION_STOP = "com.edith.ACTION_STOP"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRuntime(startId, activateAfterStart = false)
            ACTION_START_AND_ACTIVATE -> startRuntime(startId, activateAfterStart = true)
            NotificationHelper.ACTION_ACTIVATE -> {
                val detector = activationDetector
                if (detector == null) {
                    Log.w(TAG, "Activation ignored: runtime is not started")
                    stopSelf(startId)
                } else {
                    detector.triggerActivation()
                }
            }
            ACTION_STOP -> stopSelf()
            else -> {
                // Null intent (system restart) or unknown action: never start the microphone here.
                Log.w(TAG, "Ignoring start without a valid EDITH action")
                if (loop == null) stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRuntime(startId: Int, activateAfterStart: Boolean) {
        try {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.buildForegroundNotification(
                    this, NotificationHelper.statusText(this, EdithState.STANDBY, needsSetup = false)
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            // SecurityException (RECORD_AUDIO missing) or ForegroundServiceStartNotAllowedException.
            Log.e(TAG, "Cannot start microphone foreground service: ${e.javaClass.simpleName}")
            EdithServiceStatus.reportProblem(EdithServiceStatus.Problem.START_FAILED)
            EdithServiceStatus.markStopped()
            stopSelf(startId)
            return
        }

        val created = loop == null
        if (created) createRuntime()
        EdithServiceStatus.markRunning()
        Log.i(TAG, "EDITH service running in foreground")

        val runningLoop = loop ?: return
        if (activateAfterStart) {
            runningLoop.activate()
        } else if (created) {
            runningLoop.startupGreeting()
        }
    }

    private fun createRuntime() {
        val output = AndroidVoiceOutput(this)
        val input = AndroidVoiceInput(this)
        val detector = ManualActivationDetector()
        val newLoop = InteractionLoop(
            core = EdithApplication.getInstance().edithCore,
            voiceInput = input,
            voiceOutput = output,
            scheduler = HandlerScheduler(mainHandler),
            listener = this
        )
        voiceInput = input
        voiceOutput = output
        activationDetector = detector
        loop = newLoop

        detector.startDetecting { newLoop.activate() }
        output.initialize { status ->
            if (status != VoiceOutputStatus.READY) {
                Log.w(TAG, "Voice output unavailable: $status")
                EdithServiceStatus.reportProblem(EdithServiceStatus.Problem.TTS_UNAVAILABLE)
                updateNotification(EdithServiceStatus.snapshot.state)
            }
        }
    }

    // ---- LoopListener ----------------------------------------------------------------

    override fun onStateChanged(state: EdithState) {
        if (state == EdithState.LISTENING) EdithServiceStatus.clearProblem()
        EdithServiceStatus.setState(state)
        updateNotification(state)
    }

    override fun onInputError(error: VoiceError) {
        EdithServiceStatus.problemFor(error)?.let { EdithServiceStatus.reportProblem(it) }
    }

    override fun onOutputFailure() {
        EdithServiceStatus.reportProblem(EdithServiceStatus.Problem.TTS_UNAVAILABLE)
    }

    override fun onDiagnostic(message: String) {
        Log.w(TAG, message)
    }

    private fun updateNotification(state: EdithState) {
        val needsSetup = EdithServiceStatus.snapshot.problem?.needsSetup == true
        val notification = NotificationHelper.buildForegroundNotification(
            this, NotificationHelper.statusText(this, state, needsSetup)
        )
        getSystemService(NotificationManager::class.java)
            .notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "EDITH service destroyed")
        loop?.shutdown()
        activationDetector?.destroy()
        voiceInput?.destroy()
        voiceOutput?.destroy()
        loop = null
        activationDetector = null
        voiceInput = null
        voiceOutput = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground threw ${e.javaClass.simpleName}")
        }
        EdithServiceStatus.markStopped()
        super.onDestroy()
    }
}
