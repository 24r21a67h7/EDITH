package com.edith.mobile

import com.edith.runtime.EdithState
import com.edith.voice.VoiceError
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide, in-memory view of the service for the UI (survives Activity
 * recreation; resets with the process — and the service does not outlive its
 * process because it is not sticky).
 *
 * Only ever mutated on the main thread (by the service). Contains no user speech.
 */
object EdithServiceStatus {

    /** Non-sensitive reasons something is wrong. [needsSetup] problems need user action. */
    enum class Problem(val needsSetup: Boolean) {
        STT_UNAVAILABLE(true),
        STT_LANGUAGE_MISSING(true),
        MIC_PERMISSION(true),
        TTS_UNAVAILABLE(true),
        START_FAILED(false),
        AUDIO(false),
        OTHER(false)
    }

    data class Snapshot(
        val running: Boolean = false,
        val state: EdithState = EdithState.STANDBY,
        val problem: Problem? = null
    )

    @Volatile
    var snapshot: Snapshot = Snapshot()
        private set

    private val listeners = CopyOnWriteArraySet<(Snapshot) -> Unit>()

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun markRunning() = publish(snapshot.copy(running = true))

    fun markStopped() = publish(snapshot.copy(running = false, state = EdithState.STANDBY))

    fun setState(state: EdithState) = publish(snapshot.copy(state = state))

    fun reportProblem(problem: Problem) = publish(snapshot.copy(problem = problem))

    fun clearProblem() {
        if (snapshot.problem != null) publish(snapshot.copy(problem = null))
    }

    /** Maps a voice error to a user-visible problem; null for routine/transient ones (no match, silence, busy). */
    fun problemFor(error: VoiceError): Problem? = when (error) {
        VoiceError.RECOGNIZER_UNAVAILABLE -> Problem.STT_UNAVAILABLE
        VoiceError.LANGUAGE_PACK_MISSING -> Problem.STT_LANGUAGE_MISSING
        VoiceError.PERMISSION_DENIED -> Problem.MIC_PERMISSION
        VoiceError.NETWORK_REQUIRED -> Problem.STT_UNAVAILABLE
        VoiceError.AUDIO_ERROR -> Problem.AUDIO
        VoiceError.OTHER -> Problem.OTHER
        VoiceError.NO_MATCH,
        VoiceError.SPEECH_TIMEOUT,
        VoiceError.RECOGNIZER_BUSY -> null
    }

    private fun publish(new: Snapshot) {
        snapshot = new
        listeners.forEach { it(new) }
    }
}
