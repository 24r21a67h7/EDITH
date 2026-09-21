package com.edith.voice

/**
 * Interface for voice input (speech-to-text).
 *
 * Abstracts the speech recognition implementation so the core layer
 * is not coupled to Android's SpeechRecognizer or any specific engine.
 *
 * Implementations:
 * - [com.edith.mobile.AndroidVoiceInput] — Android on-device STT
 * - Future: Sherpa-ONNX, Whisper, etc. (not decided)
 *
 * Contract: after [startListening], exactly one of `onResult` / `onError` is
 * delivered, on the thread that owns the runtime loop (main thread on Android),
 * unless [stopListening] or [destroy] is called first, in which case nothing is
 * delivered. Transcripts are never logged by implementations.
 */
interface VoiceInput {

    /**
     * Starts listening for voice input.
     *
     * @param onResult Called with the transcribed text when recognition completes.
     * @param onError Called with a typed reason if recognition fails.
     */
    fun startListening(onResult: (String) -> Unit, onError: (VoiceError) -> Unit)

    /**
     * Aborts any active listening session. No callbacks are delivered for the
     * aborted session after this returns.
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
