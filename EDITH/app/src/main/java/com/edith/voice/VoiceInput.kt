package com.edith.voice

/**
 * Interface for voice input (speech-to-text).
 *
 * Abstracts the speech recognition implementation so the core layer
 * is not coupled to Android's SpeechRecognizer or any specific engine.
 *
 * Implementations:
 * - [com.edith.mobile.AndroidVoiceInput] — Android on-device STT (Mission 01)
 * - Future: Sherpa-ONNX, Whisper, etc.
 */
interface VoiceInput {

    /**
     * Starts listening for voice input.
     *
     * @param onResult Called with the transcribed text when recognition completes.
     * @param onError Called with an error message if recognition fails.
     */
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)

    /**
     * Stops any active listening session.
     */
    fun stopListening()

    /**
     * Checks whether voice input is available on this device.
     */
    fun isAvailable(): Boolean

    /**
     * Releases all resources held by this voice input instance.
     */
    fun destroy()
}
