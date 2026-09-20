package com.edith.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.edith.voice.VoiceInput

/**
 * Android implementation of [VoiceInput] using on-device speech recognition.
 *
 * Uses [SpeechRecognizer.createOnDeviceSpeechRecognizer] for fully offline
 * speech-to-text. No audio data leaves the device.
 *
 * Requirements:
 * - Android 12+ (API 31) for on-device recognizer
 * - RECORD_AUDIO runtime permission must be granted
 * - Offline English language pack must be installed on device
 *
 * Must be created and used on the main thread (Android SpeechRecognizer requirement).
 */
class AndroidVoiceInput(private val context: Context) : VoiceInput {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val TAG = "EDITH.VoiceInput"
    }

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!isAvailable()) {
            onError("On-device speech recognition is not available. Please ensure the offline language pack is installed.")
            return
        }

        // Create a fresh recognizer for each session (Android best practice)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed — could be used for UI visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Raw audio buffer — not needed for basic recognition
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error (should not occur in offline mode)"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout (should not occur in offline mode)"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error (should not occur in offline mode)"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported for on-device recognition"
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Offline language pack not installed"
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many recognition requests"
                    else -> "Unknown speech recognition error ($error)"
                }
                Log.w(TAG, "Recognition error: $errorMessage")
                onError(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()

                if (!text.isNullOrBlank()) {
                    Log.i(TAG, "Recognized: $text")
                    onResult(text)
                } else {
                    onError("No speech recognized")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Not used — we wait for final results
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Reserved for future use
            }
        })

        speechRecognizer?.startListening(intent)
        Log.d(TAG, "Listening started (on-device)")
    }

    override fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    override fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
