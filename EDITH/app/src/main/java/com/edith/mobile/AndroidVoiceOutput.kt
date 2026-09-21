package com.edith.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.edith.voice.SpeechOutcome
import com.edith.voice.VoiceOutput
import com.edith.voice.VoiceOutputStatus
import java.util.Locale
import java.util.UUID

/**
 * Android implementation of [VoiceOutput] using the built-in TextToSpeech engine,
 * restricted to voices that are INSTALLED and do not need the network.
 *
 * There is no silent fallback: if no installed, non-network English voice can be
 * found (or confirmed), [status] becomes [VoiceOutputStatus.NO_OFFLINE_VOICE] and
 * [speak] finishes with [SpeechOutcome.FAILED]. If the engine later reports a
 * network / not-installed synthesis error, the status is downgraded the same way.
 *
 * Every [speak] call finishes exactly once, on the main thread (the engine's
 * binder-thread callbacks are re-posted to main). Speech requested while the engine
 * is still initializing is queued until initialization completes.
 *
 * Speech uses USAGE_ASSISTANT/CONTENT_TYPE_SPEECH attributes and requests transient
 * "may duck" audio focus for its duration. NOT verified on a physical device yet.
 * Utterance text is never logged.
 */
class AndroidVoiceOutput(private val context: Context) : VoiceOutput {

    private class Utterance(
        val id: String,
        val text: String,
        private var callback: ((SpeechOutcome) -> Unit)?
    ) {
        /** Delivers the outcome at most once. */
        fun finish(outcome: SpeechOutcome) {
            val cb = callback
            callback = null
            cb?.invoke(outcome)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val speechAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var tts: TextToSpeech? = null
    private var statusListener: ((VoiceOutputStatus) -> Unit)? = null
    private var pending: Utterance? = null
    private var active: Utterance? = null
    private var focusRequest: AudioFocusRequest? = null

    override var status: VoiceOutputStatus = VoiceOutputStatus.NOT_INITIALIZED
        private set

    companion object {
        private const val TAG = "EDITH.VoiceOutput"
    }

    /**
     * Starts the (asynchronous) engine initialization. [onStatus] is called on the
     * main thread with the final status: READY, ENGINE_UNAVAILABLE or NO_OFFLINE_VOICE.
     */
    fun initialize(onStatus: ((VoiceOutputStatus) -> Unit)? = null) {
        if (tts != null) return
        statusListener = onStatus
        status = VoiceOutputStatus.INITIALIZING
        try {
            tts = TextToSpeech(context) { code -> mainHandler.post { onEngineInit(code) } }
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech construction failed: ${e.javaClass.simpleName}")
            setStatus(VoiceOutputStatus.ENGINE_UNAVAILABLE)
            failPending()
        }
    }

    private fun onEngineInit(code: Int) {
        val engine = tts ?: return // destroyed before init finished
        if (code != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS engine initialization failed (code $code)")
            setStatus(VoiceOutputStatus.ENGINE_UNAVAILABLE)
            failPending()
            return
        }

        engine.setOnUtteranceProgressListener(progressListener)
        if (engine.setAudioAttributes(speechAttributes) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Could not set speech audio attributes")
        }

        if (selectOfflineVoice(engine)) {
            setStatus(VoiceOutputStatus.READY)
            val queued = pending
            pending = null
            if (queued != null) startUtterance(queued)
        } else {
            setStatus(VoiceOutputStatus.NO_OFFLINE_VOICE)
            failPending()
        }
    }

    /** Picks an installed, non-network English voice. Returns false if none can be confirmed. */
    private fun selectOfflineVoice(engine: TextToSpeech): Boolean {
        val voices: Set<Voice>? = try {
            engine.voices
        } catch (e: Exception) {
            Log.w(TAG, "getVoices() threw ${e.javaClass.simpleName}")
            null
        }

        val candidate = voices
            ?.filter { isInstalledOfflineEnglish(it) }
            ?.sortedWith(
                compareByDescending<Voice> { it.locale == Locale.US }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
            ?.firstOrNull()

        if (candidate == null) {
            Log.w(TAG, "No installed offline English voice (engine reported ${voices?.size ?: 0} voices)")
            return false
        }
        if (engine.setVoice(candidate) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Engine refused offline voice ${candidate.name}")
            return false
        }
        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
        Log.i(TAG, "Selected offline voice: ${candidate.name}")
        return true
    }

    // KEY_FEATURE_NETWORK_SYNTHESIS is deprecated, but some engines still flag network voices with it
    // instead of Voice.isNetworkConnectionRequired; checking both keeps the offline guarantee strict.
    @Suppress("DEPRECATION")
    private fun isInstalledOfflineEnglish(voice: Voice): Boolean {
        val features: Set<String> = voice.features ?: emptySet()
        return voice.locale?.language == "en" &&
            !voice.isNetworkConnectionRequired &&
            !features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) &&
            !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
    }

    override fun speak(text: String, onFinished: (SpeechOutcome) -> Unit) {
        val utterance = Utterance(UUID.randomUUID().toString(), text, onFinished)
        when (status) {
            VoiceOutputStatus.READY -> startUtterance(utterance)
            VoiceOutputStatus.INITIALIZING -> {
                pending?.finish(SpeechOutcome.INTERRUPTED)
                pending = utterance
            }
            else -> mainHandler.post { utterance.finish(SpeechOutcome.FAILED) }
        }
    }

    private fun startUtterance(utterance: Utterance) {
        val engine = tts
        if (engine == null) {
            utterance.finish(SpeechOutcome.FAILED)
            return
        }
        active?.let { complete(it, SpeechOutcome.INTERRUPTED) }
        active = utterance
        requestAudioFocus()

        val result = try {
            engine.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, null, utterance.id)
        } catch (e: Exception) {
            Log.e(TAG, "speak() threw ${e.javaClass.simpleName}")
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "speak() was not accepted (code $result)")
            complete(utterance, SpeechOutcome.FAILED)
        }
    }

    private fun complete(utterance: Utterance, outcome: SpeechOutcome) {
        if (active === utterance) {
            active = null
            abandonAudioFocus()
        }
        utterance.finish(outcome)
    }

    private fun failPending() {
        val queued = pending ?: return
        pending = null
        queued.finish(SpeechOutcome.FAILED)
    }

    override fun stop() {
        pending?.let {
            pending = null
            it.finish(SpeechOutcome.INTERRUPTED)
        }
        val current = active
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop() threw ${e.javaClass.simpleName}")
        }
        // Do not wait for the engine's onStop: finish now; a later callback is ignored (exactly-once).
        if (current != null) complete(current, SpeechOutcome.INTERRUPTED)
    }

    override fun destroy() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "shutdown() threw ${e.javaClass.simpleName}")
        }
        tts = null
        status = VoiceOutputStatus.NOT_INITIALIZED
    }

    private fun setStatus(newStatus: VoiceOutputStatus) {
        status = newStatus
        statusListener?.invoke(newStatus)
    }

    // Engine callbacks arrive on a binder thread: re-post to main and match by utterance id.
    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) = finishFromEngine(utteranceId, SpeechOutcome.COMPLETED)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finishFromEngine(utteranceId, SpeechOutcome.FAILED)

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "TTS error code $errorCode")
            if (errorCode == TextToSpeech.ERROR_NETWORK ||
                errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT ||
                errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET
            ) {
                // The selected voice turned out to need the network / data: stop pretending it is offline.
                mainHandler.post { if (tts != null) setStatus(VoiceOutputStatus.NO_OFFLINE_VOICE) }
            }
            finishFromEngine(utteranceId, SpeechOutcome.FAILED)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            finishFromEngine(utteranceId, SpeechOutcome.INTERRUPTED)
    }

    private fun finishFromEngine(utteranceId: String?, outcome: SpeechOutcome) {
        mainHandler.post {
            val current = active
            if (current != null && current.id == utteranceId) complete(current, outcome)
        }
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        val result = manager.requestAudioFocus(request)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Android 17 can refuse audio focus silently to apps without a valid foreground service.
            Log.w(TAG, "Audio focus not granted (result $result); speaking anyway")
        }
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        audioManager?.abandonAudioFocusRequest(request)
    }
}
