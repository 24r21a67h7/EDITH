package com.edith.voice

/**
 * Interface for voice output (text-to-speech).
 *
 * Abstracts the TTS implementation so the core layer
 * is not coupled to Android's TextToSpeech or any specific engine.
 *
 * Implementations:
 * - [com.edith.mobile.AndroidVoiceOutput] — Android built-in TTS (Mission 01)
 * - Future: Sherpa-ONNX Piper/VITS, custom neural TTS, etc.
 */
interface VoiceOutput {

    /**
     * Speaks the given text aloud.
     *
     * @param text The text to synthesize and speak.
     * @param onDone Optional callback invoked when speech completes.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null)

    /**
     * Stops any ongoing speech immediately.
     */
    fun stop()

    /**
     * Checks whether voice output is available on this device.
     */
    fun isAvailable(): Boolean

    /**
     * Releases all resources held by this voice output instance.
     */
    fun destroy()
}
