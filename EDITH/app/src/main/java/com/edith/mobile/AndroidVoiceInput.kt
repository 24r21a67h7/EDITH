package com.edith.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.edith.voice.LanguageSelection
import com.edith.voice.SttReadiness
import com.edith.voice.VoiceError
import com.edith.voice.VoiceInput

/**
 * Android implementation of [VoiceInput] using the ON-DEVICE speech recognizer
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer]). It never falls back to a
 * network recognizer; if the on-device recognizer or its language pack is
 * missing, the failure is reported as a typed [VoiceError].
 *
 * The recognition language is chosen from the packs actually installed
 * (en-US preferred, otherwise another English variant such as en-IN).
 *
 * Privacy: transcripts are handed to the caller and never logged here.
 *
 * Must be created and used on the main thread (SpeechRecognizer requirement).
 * NOT verified on a physical device yet.
 */
class AndroidVoiceInput(private val context: Context) : VoiceInput {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /** Incremented for every new session and by [stopListening]; stale callbacks compare against it. */
    private var session = 0L

    /** Installed English pack found by the support check (null until known). */
    private var resolvedLanguage: String? = null

    companion object {
        private const val TAG = "EDITH.VoiceInput"
        private const val DEFAULT_LANGUAGE = "en-US"
    }

    override fun isAvailable(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /**
     * First-run / setup check: is an on-device recognizer present, and is an
     * English pack installed? Never downloads anything. [onResult] is called on the main thread.
     */
    fun checkReadiness(onResult: (SttReadiness) -> Unit) {
        if (!isAvailable()) {
            onResult(SttReadiness.RECOGNIZER_UNAVAILABLE)
            return
        }
        val checker = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create recognizer for support check: ${e.javaClass.simpleName}")
            onResult(SttReadiness.CHECK_FAILED)
            return
        }
        try {
            checker.checkRecognitionSupport(
                languageIntent(DEFAULT_LANGUAGE),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        val language = LanguageSelection.pickEnglish(support.installedOnDeviceLanguages)
                        mainHandler.post { checker.destroy() }
                        if (language != null) {
                            resolvedLanguage = language
                            onResult(SttReadiness.READY)
                        } else {
                            onResult(SttReadiness.LANGUAGE_PACK_MISSING)
                        }
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "Recognition support check failed (code $error)")
                        mainHandler.post { checker.destroy() }
                        onResult(SttReadiness.CHECK_FAILED)
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Recognition support check threw ${e.javaClass.simpleName}")
            checker.destroy()
            onResult(SttReadiness.CHECK_FAILED)
        }
    }

    override fun startListening(onResult: (String) -> Unit, onError: (VoiceError) -> Unit) {
        val fail = onError
        release()
        val token = ++session

        if (!isAvailable()) {
            fail(VoiceError.RECOGNIZER_UNAVAILABLE)
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail(VoiceError.PERMISSION_DENIED)
            return
        }

        val created = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create on-device recognizer: ${e.javaClass.simpleName}")
            fail(VoiceError.RECOGNIZER_UNAVAILABLE)
            return
        }
        recognizer = created

        val knownLanguage = resolvedLanguage
        if (knownLanguage != null) {
            begin(created, token, knownLanguage, onResult, fail)
            return
        }

        // First use in this process: pick the language from the installed packs.
        try {
            created.checkRecognitionSupport(
                languageIntent(DEFAULT_LANGUAGE),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (token != session) return
                        val language = LanguageSelection.pickEnglish(support.installedOnDeviceLanguages)
                        if (language == null) {
                            release()
                            fail(VoiceError.LANGUAGE_PACK_MISSING)
                        } else {
                            resolvedLanguage = language
                            begin(created, token, language, onResult, fail)
                        }
                    }

                    override fun onError(error: Int) {
                        if (token != session) return
                        Log.w(TAG, "Support check failed (code $error); trying $DEFAULT_LANGUAGE")
                        begin(created, token, DEFAULT_LANGUAGE, onResult, fail)
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Support check threw ${e.javaClass.simpleName}; trying $DEFAULT_LANGUAGE")
            begin(created, token, DEFAULT_LANGUAGE, onResult, fail)
        }
    }

    private fun begin(
        target: SpeechRecognizer,
        token: Long,
        language: String,
        onResult: (String) -> Unit,
        fail: (VoiceError) -> Unit
    ) {
        if (token != session) return

        target.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                if (token != session) return
                val mapped = mapError(error)
                Log.w(TAG, "Recognition error code $error -> $mapped")
                release()
                fail(mapped)
            }

            override fun onResults(results: Bundle?) {
                if (token != session) return
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                release()
                if (text.isNullOrBlank()) {
                    fail(VoiceError.NO_MATCH)
                } else {
                    onResult(text) // never logged
                }
            }
        })

        try {
            target.startListening(languageIntent(language))
            Log.d(TAG, "Listening started (on-device, $language)")
        } catch (e: Exception) {
            Log.w(TAG, "startListening threw ${e.javaClass.simpleName}")
            release()
            fail(VoiceError.OTHER)
        }
    }

    private fun mapError(error: Int): VoiceError = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> VoiceError.AUDIO_ERROR
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION_DENIED
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError.NETWORK_REQUIRED
        SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceError.RECOGNIZER_BUSY
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceError.LANGUAGE_PACK_MISSING
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> VoiceError.RECOGNIZER_UNAVAILABLE
        else -> VoiceError.OTHER // includes ERROR_CLIENT, ERROR_SERVER, ERROR_CANNOT_CHECK_SUPPORT
    }

    private fun languageIntent(language: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    /** Aborts the current session; no callback is delivered for it afterwards. */
    override fun stopListening() {
        session++
        release()
    }

    override fun destroy() {
        stopListening()
    }

    private fun release() {
        val current = recognizer ?: return
        recognizer = null
        try {
            current.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "cancel() threw ${e.javaClass.simpleName}")
        }
        try {
            current.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy() threw ${e.javaClass.simpleName}")
        }
    }
}
