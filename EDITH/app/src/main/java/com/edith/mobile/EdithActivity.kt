package com.edith.mobile

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.edith.R
import com.edith.runtime.EdithState
import com.edith.voice.SttReadiness
import com.edith.voice.VoiceOutputStatus

/**
 * EDITH's main (and only) Activity.
 *
 * - Requests permissions just-in-time (only when the user taps Start/Activate), never on launch.
 * - Shows a setup checklist: microphone permission, on-device speech recognition, offline voice.
 * - Offers the right fix for whatever is wrong (grant permission / app settings when permanently
 *   denied / speech settings / voice data install).
 * - Reflects the real service state (via [EdithServiceStatus]) instead of guessing.
 *
 * It never displays recognized speech. The service must be started from this visible Activity
 * (Android 14+ microphone foreground-service rules).
 *
 * NOT verified on a physical device yet.
 */
class EdithActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var activateButton: Button
    private lateinit var serviceButton: Button
    private lateinit var setupButton: Button

    private enum class PendingStart { NONE, START, START_AND_ACTIVATE }

    private var pendingStart = PendingStart.NONE
    private var sttReadiness: SttReadiness? = null
    private var ttsStatus: VoiceOutputStatus? = null
    private var sttChecker: AndroidVoiceInput? = null
    private var ttsChecker: AndroidVoiceOutput? = null
    private var setupAction: (() -> Unit)? = null

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private val statusListener: (EdithServiceStatus.Snapshot) -> Unit = { refreshUi() }

    companion object {
        private const val TAG = "EDITH.Activity"
        private const val PREFS = "edith_setup"
        private const val KEY_MIC_BLOCKED = "mic_permission_blocked"
        private const val KEY_NOTIFICATIONS_ASKED = "notification_permission_asked"
        private const val STATE_PENDING = "pending_start"
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            prefs.edit().putBoolean(KEY_MIC_BLOCKED, false).apply()
            continueStart()
        } else {
            // Denied without a rationale on offer = Android will not show the dialog again.
            val blocked = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            prefs.edit().putBoolean(KEY_MIC_BLOCKED, blocked).apply()
            Log.w(TAG, "Microphone permission denied (blocked=$blocked)")
            pendingStart = PendingStart.NONE
            refreshUi()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notifications are optional: start regardless of the answer.
        launchPendingStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edith)

        statusText = findViewById(R.id.statusText)
        detailText = findViewById(R.id.responseText)
        activateButton = findViewById(R.id.activateButton)
        serviceButton = findViewById(R.id.serviceButton)
        setupButton = findViewById(R.id.setupButton)

        savedInstanceState?.getString(STATE_PENDING)?.let {
            pendingStart = runCatching { PendingStart.valueOf(it) }.getOrDefault(PendingStart.NONE)
        }

        activateButton.setOnClickListener { onActivateClicked() }
        serviceButton.setOnClickListener { onServiceButtonClicked() }
        setupButton.setOnClickListener { setupAction?.invoke() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING, pendingStart.name)
    }

    override fun onStart() {
        super.onStart()
        EdithServiceStatus.addListener(statusListener)
    }

    override fun onResume() {
        super.onResume()
        if (hasMicPermission()) prefs.edit().putBoolean(KEY_MIC_BLOCKED, false).apply()
        runSetupChecks()
        refreshUi()
    }

    override fun onStop() {
        EdithServiceStatus.removeListener(statusListener)
        releaseCheckers()
        super.onStop()
    }

    // ---- start / activate ------------------------------------------------------------

    private fun onServiceButtonClicked() {
        if (EdithServiceStatus.snapshot.running) {
            stopService(Intent(this, EdithForegroundService::class.java))
        } else {
            requestStart(PendingStart.START)
        }
    }

    private fun onActivateClicked() {
        val snapshot = EdithServiceStatus.snapshot
        if (snapshot.running) {
            if (snapshot.state == EdithState.STANDBY) {
                startService(
                    Intent(this, EdithForegroundService::class.java).apply {
                        action = NotificationHelper.ACTION_ACTIVATE
                    }
                )
            }
        } else {
            requestStart(PendingStart.START_AND_ACTIVATE)
        }
    }

    private fun requestStart(kind: PendingStart) {
        pendingStart = kind
        if (hasMicPermission()) {
            continueStart()
            return
        }
        if (prefs.getBoolean(KEY_MIC_BLOCKED, false)) {
            pendingStart = PendingStart.NONE
            openAppSettings()
            return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.mic_permission_rationale)
                .setPositiveButton(R.string.dialog_continue) { _, _ ->
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                .setNegativeButton(R.string.dialog_cancel) { _, _ -> pendingStart = PendingStart.NONE }
                .show()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Microphone is granted; ask for notifications at most once, then start. */
    private fun continueStart() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission && !prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)) {
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchPendingStart()
        }
    }

    private fun launchPendingStart() {
        val kind = pendingStart
        pendingStart = PendingStart.NONE
        if (kind == PendingStart.NONE) {
            refreshUi()
            return
        }
        val startAction = if (kind == PendingStart.START_AND_ACTIVATE) {
            EdithForegroundService.ACTION_START_AND_ACTIVATE
        } else {
            EdithForegroundService.ACTION_START
        }
        try {
            startForegroundService(
                Intent(this, EdithForegroundService::class.java).apply { action = startAction }
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed: ${e.javaClass.simpleName}")
            EdithServiceStatus.reportProblem(EdithServiceStatus.Problem.START_FAILED)
        }
    }

    // ---- setup checks ----------------------------------------------------------------

    private fun runSetupChecks() {
        releaseCheckers()
        sttReadiness = null
        ttsStatus = null

        val stt = AndroidVoiceInput(applicationContext)
        sttChecker = stt
        stt.checkReadiness { result ->
            if (sttChecker === stt) {
                sttReadiness = result
                refreshUi()
            }
        }

        val tts = AndroidVoiceOutput(applicationContext)
        ttsChecker = tts
        tts.initialize { status ->
            if (ttsChecker === tts) {
                ttsStatus = status
                ttsChecker = null
                refreshUi()
                detailText.post { tts.destroy() }
            }
        }
    }

    private fun releaseCheckers() {
        sttChecker?.destroy()
        sttChecker = null
        ttsChecker?.destroy()
        ttsChecker = null
    }

    // ---- UI --------------------------------------------------------------------------

    private fun refreshUi() {
        val snapshot = EdithServiceStatus.snapshot
        val micOk = hasMicPermission()
        val micBlocked = !micOk && prefs.getBoolean(KEY_MIC_BLOCKED, false)
        val sttUnusable = sttReadiness == SttReadiness.RECOGNIZER_UNAVAILABLE ||
            sttReadiness == SttReadiness.LANGUAGE_PACK_MISSING

        statusText.text = when {
            !micOk -> getString(R.string.status_mic_needed)
            !snapshot.running -> getString(R.string.status_offline)
            snapshot.state == EdithState.LISTENING -> getString(R.string.status_listening)
            snapshot.state == EdithState.PROCESSING -> getString(R.string.status_processing)
            snapshot.state == EdithState.SPEAKING -> getString(R.string.status_speaking)
            else -> getString(R.string.status_active)
        }

        serviceButton.text = getString(if (snapshot.running) R.string.stop_edith else R.string.start_edith)
        activateButton.isEnabled = micOk && !sttUnusable &&
            (!snapshot.running || snapshot.state == EdithState.STANDBY)

        val lines = mutableListOf<String>()
        lines += getString(
            when {
                micOk -> R.string.setup_mic_granted
                micBlocked -> R.string.setup_mic_blocked
                else -> R.string.setup_mic_needed
            }
        )
        lines += getString(
            when (sttReadiness) {
                null -> R.string.setup_stt_checking
                SttReadiness.READY -> R.string.setup_stt_ready
                SttReadiness.RECOGNIZER_UNAVAILABLE -> R.string.setup_stt_unavailable
                SttReadiness.LANGUAGE_PACK_MISSING -> R.string.setup_stt_language_missing
                SttReadiness.CHECK_FAILED -> R.string.setup_stt_check_failed
            }
        )
        lines += getString(
            when (ttsStatus) {
                null, VoiceOutputStatus.NOT_INITIALIZED, VoiceOutputStatus.INITIALIZING ->
                    R.string.setup_tts_checking
                VoiceOutputStatus.READY -> R.string.setup_tts_ready
                VoiceOutputStatus.ENGINE_UNAVAILABLE -> R.string.setup_tts_engine_unavailable
                VoiceOutputStatus.NO_OFFLINE_VOICE -> R.string.setup_tts_no_voice
            }
        )
        snapshot.problem?.let { lines += getString(R.string.problem_last, getString(problemText(it))) }
        detailText.text = lines.joinToString("\n")

        configureSetupButton(micOk, micBlocked)
    }

    private fun problemText(problem: EdithServiceStatus.Problem): Int = when (problem) {
        EdithServiceStatus.Problem.STT_UNAVAILABLE -> R.string.problem_stt_unavailable
        EdithServiceStatus.Problem.STT_LANGUAGE_MISSING -> R.string.problem_stt_language_missing
        EdithServiceStatus.Problem.MIC_PERMISSION -> R.string.problem_mic_permission
        EdithServiceStatus.Problem.TTS_UNAVAILABLE -> R.string.problem_tts_unavailable
        EdithServiceStatus.Problem.START_FAILED -> R.string.problem_start_failed
        EdithServiceStatus.Problem.AUDIO -> R.string.problem_audio
        EdithServiceStatus.Problem.OTHER -> R.string.problem_other
    }

    /** One contextual fix button, highest-priority problem first. */
    private fun configureSetupButton(micOk: Boolean, micBlocked: Boolean) {
        val label: Int?
        val action: (() -> Unit)?
        when {
            !micOk && micBlocked -> {
                label = R.string.action_open_app_settings
                action = { openAppSettings() }
            }
            !micOk -> {
                label = R.string.action_grant_microphone
                action = { requestMicOnly() }
            }
            sttReadiness == SttReadiness.RECOGNIZER_UNAVAILABLE ||
                sttReadiness == SttReadiness.LANGUAGE_PACK_MISSING -> {
                label = R.string.action_open_speech_settings
                action = { openSpeechSettings() }
            }
            ttsStatus == VoiceOutputStatus.NO_OFFLINE_VOICE -> {
                label = R.string.action_install_voice_data
                action = { installVoiceData() }
            }
            ttsStatus == VoiceOutputStatus.ENGINE_UNAVAILABLE -> {
                label = R.string.action_open_tts_settings
                action = { openTtsSettings() }
            }
            else -> {
                label = null
                action = null
            }
        }
        setupAction = action
        if (label == null) {
            setupButton.visibility = View.GONE
        } else {
            setupButton.setText(label)
            setupButton.visibility = View.VISIBLE
        }
    }

    // ---- helpers ---------------------------------------------------------------------

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Setup-button path: grant the microphone without starting anything. */
    private fun requestMicOnly() {
        pendingStart = PendingStart.NONE
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun openAppSettings() {
        openSettings(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    private fun openSpeechSettings() = openSettings(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))

    private fun installVoiceData() = openSettings(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))

    private fun openTtsSettings() = openSettings(Intent("com.android.settings.TTS_SETTINGS"))

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                Log.w(TAG, "No settings screen could be opened")
                Toast.makeText(this, R.string.action_open_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
