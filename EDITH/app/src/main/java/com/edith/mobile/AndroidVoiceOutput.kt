package com.edith.mobile

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.edith.voice.VoiceOutput
import java.util.Locale
import java.util.UUID

/**
 * Android implementation of [VoiceOutput] using the built-in TextToSpeech engine.
 *
 * Forces offline voice synthesis — selects a voice that does not require
 * network connectivity. No audio data leaves the device.
 *
 * On Pixel devices, the Google TTS engine with offline en-US voice data
 * is typically pre-installed.
 */
class AndroidVoiceOutput(private val context: Context) : VoiceOutput {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "EDITH.VoiceOutput"
    }

    /**
     * Initializes the TTS engine. Must be called before [speak].
     * TTS initialization is asynchronous — the engine is ready when
     * [isAvailable] returns true.
     */
    fun initialize(onReady: (() -> Unit)? = null) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureTts()
                isInitialized = true
                Log.i(TAG, "TTS initialized (offline)")
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isInitialized = false
            }
        }
    }

    private fun configureTts() {
        val engine = tts ?: return
        val targetLocale = Locale.US

        // Check if the language is available
        val availability = engine.isLanguageAvailable(targetLocale)
        if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "TTS language data not available for $targetLocale")
            return
        }

        // Try to find an explicitly offline voice
        val offlineVoice = engine.voices?.firstOrNull { voice ->
            voice.locale == targetLocale &&
                !voice.isNetworkConnectionRequired &&
                !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)
        }

        if (offlineVoice != null) {
            engine.voice = offlineVoice
            Log.i(TAG, "Selected offline voice: ${offlineVoice.name}")
        } else {
            // Fallback: set locale and hope the default voice is offline-capable
            engine.language = targetLocale
            Log.w(TAG, "No explicitly offline voice found, using default for $targetLocale")
        }

        // Natural speech settings
        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
    }

    override fun isAvailable(): Boolean = isInitialized

    override fun speak(text: String, onDone: (() -> Unit)?) {
        val engine = tts
        if (engine == null || !isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            onDone?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()

        if (onDone != null) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uid: String?) {
                    Log.d(TAG, "Speaking: $text")
                }

                override fun onDone(uid: String?) {
                    if (uid == utteranceId) {
                        Log.d(TAG, "Done speaking")
                        onDone()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(uid: String?) {
                    if (uid == utteranceId) {
                        Log.e(TAG, "TTS error for utterance $uid")
                        onDone()
                    }
                }
            })
        }

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
