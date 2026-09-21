package com.edith.voice

/**
 * Interface for voice output (text-to-speech).
 *
 * Abstracts the TTS implementation so the core layer
 * is not coupled to Android's TextToSpeech or any specific engine.
 *
 * Implementations:
 * - [com.edith.mobile.AndroidVoiceOutput] — Android TTS, restricted to installed (offline) voices
 * - Future: Sherpa-ONNX Piper/VITS, custom neural TTS, etc. (not decided)
 *
 * Contract: every call to [speak] results in `onFinished` being invoked exactly
 * once (COMPLETED, INTERRUPTED or FAILED), on the thread that owns the runtime
 * loop, unless the output is destroyed first.
 */
interface VoiceOutput {

    /** Current readiness; only [VoiceOutputStatus.READY] can speak. */
    val status: VoiceOutputStatus

    /**
     * Speaks the given text aloud.
     *
     * @param text The text to synthesize and speak.
     * @param onFinished Invoked exactly once when speech ends, is interrupted, or fails.
     */
    fun speak(text: String, onFinished: (SpeechOutcome) -> Unit)

    /**
     * Stops any ongoing (or queued) speech. The affected [speak] call finishes
     * with [SpeechOutcome.INTERRUPTED].
     */
    fun stop()

    /** Convenience: true when [status] is [VoiceOutputStatus.READY]. */
    fun isAvailable(): Boolean = status == VoiceOutputStatus.READY

    /**
     * Releases all resources held by this voice output instance.
     */
    fun destroy()
}
