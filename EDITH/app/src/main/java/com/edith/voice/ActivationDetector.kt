package com.edith.voice

/**
 * Interface for EDITH activation detection.
 *
 * Abstracts the mechanism that triggers EDITH to start listening.
 * In Mission 01, this is a manual push-to-talk trigger.
 * In future missions, this will be a wake-word engine ("Hey EDITH").
 *
 * Implementations:
 * - [com.edith.mobile.ManualActivationDetector] — Button/notification trigger (Mission 01)
 * - Future: Porcupine, openWakeWord, Sherpa-ONNX keyword spotter
 */
interface ActivationDetector {

    /**
     * Starts detecting activation events.
     *
     * @param onActivated Called each time EDITH is activated.
     */
    fun startDetecting(onActivated: () -> Unit)

    /**
     * Stops detecting activation events.
     */
    fun stopDetecting()

    /**
     * Whether the detector is currently active and listening for triggers.
     */
    val isActive: Boolean

    /**
     * Releases all resources held by this detector.
     */
    fun destroy()
}
