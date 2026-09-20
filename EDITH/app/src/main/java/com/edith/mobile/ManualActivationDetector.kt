package com.edith.mobile

import com.edith.voice.ActivationDetector

/**
 * Manual activation detector for Mission 01.
 *
 * Instead of continuous wake-word detection, activation is triggered
 * explicitly via UI button or notification action. The architecture is
 * ready for a real wake-word engine (Porcupine, openWakeWord, etc.)
 * to be swapped in at Mission 02.
 */
class ManualActivationDetector : ActivationDetector {

    private var callback: (() -> Unit)? = null

    @Volatile
    override var isActive: Boolean = false
        private set

    override fun startDetecting(onActivated: () -> Unit) {
        callback = onActivated
        isActive = true
    }

    override fun stopDetecting() {
        isActive = false
        callback = null
    }

    /**
     * Triggers activation manually.
     * Called by UI button press or notification action.
     */
    fun triggerActivation() {
        if (isActive) {
            callback?.invoke()
        }
    }

    override fun destroy() {
        stopDetecting()
    }
}
