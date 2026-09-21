package com.edith.runtime

import com.edith.core.EdithCore
import com.edith.voice.SpeechOutcome
import com.edith.voice.VoiceError
import com.edith.voice.VoiceInput
import com.edith.voice.VoiceOutput

/** The runtime states of one EDITH voice interaction. */
enum class EdithState { STANDBY, LISTENING, PROCESSING, SPEAKING }

enum class ActivationResult {
    /** A listening (or greeting) session was started. */
    ACCEPTED,

    /** EDITH is not in STANDBY (or has been shut down); nothing was started. */
    BUSY
}

/** Handle for a scheduled task. */
fun interface Cancellable {
    fun cancel()
}

/** Delayed execution on the same thread that drives [InteractionLoop]. */
interface Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable
}

/**
 * Observer of the loop. Deliberately has NO parameter that can carry user
 * speech: transcripts and responses never leave the loop through here.
 */
interface LoopListener {
    fun onStateChanged(state: EdithState) {}

    /** A listening session failed for the given reason (before it is spoken about). */
    fun onInputError(error: VoiceError) {}

    /** Speech could not be produced (or timed out). */
    fun onOutputFailure() {}

    /** Non-sensitive diagnostic text (never includes user input). */
    fun onDiagnostic(message: String) {}
}

data class LoopConfig(
    /** Hard ceiling on one listening session; covers a recognizer that never calls back. */
    val listenTimeoutMs: Long = 15_000L,
    /** Speech watchdog = base + perChar * text length; covers a TTS engine that never calls back. */
    val speakBaseTimeoutMs: Long = 8_000L,
    val speakPerCharTimeoutMs: Long = 120L
) {
    fun speakTimeoutFor(text: String): Long = speakBaseTimeoutMs + speakPerCharTimeoutMs * text.length
}

/**
 * EDITH's runtime loop:
 *
 *     STANDBY → LISTENING → PROCESSING → SPEAKING → STANDBY
 *
 * Pure Kotlin (no Android). All calls, and all callbacks from [voiceInput] /
 * [voiceOutput] / [scheduler], must happen on ONE thread (the main thread on
 * Android). That makes every transition deterministic and testable.
 *
 * Guarantees:
 * - Every path returns to [EdithState.STANDBY]. LISTENING is bounded by
 *   [LoopConfig.listenTimeoutMs], SPEAKING by [LoopConfig.speakTimeoutFor], and
 *   exceptions from the input, output, core or listener are contained.
 * - The microphone is never opened unless the loop is in STANDBY, so EDITH
 *   cannot hear its own speech (greeting, response or error message).
 * - Callbacks from a session that already ended or timed out are ignored.
 * - Transcripts are never logged, stored, or passed to [LoopListener].
 */
class InteractionLoop(
    private val core: EdithCore,
    private val voiceInput: VoiceInput,
    private val voiceOutput: VoiceOutput,
    private val scheduler: Scheduler,
    private val listener: LoopListener = object : LoopListener {},
    private val config: LoopConfig = LoopConfig()
) {

    var state: EdithState = EdithState.STANDBY
        private set

    private var session = 0L
    private var watchdog: Cancellable? = null
    private var closed = false

    /** Starts a listening session if (and only if) EDITH is idle. */
    fun activate(): ActivationResult {
        if (closed || state != EdithState.STANDBY) return ActivationResult.BUSY

        val id = nextSession()
        setState(EdithState.LISTENING)
        armWatchdog(config.listenTimeoutMs) { onListenTimeout(id) }

        try {
            voiceInput.startListening(
                onResult = { text -> if (isCurrent(id, EdithState.LISTENING)) onTranscript(id, text) },
                onError = { error -> if (isCurrent(id, EdithState.LISTENING)) handleInputError(id, error) }
            )
        } catch (e: Exception) {
            diagnostic("Voice input threw ${e.javaClass.simpleName}")
            if (isCurrent(id, EdithState.LISTENING)) handleInputError(id, VoiceError.OTHER)
        }
        return ActivationResult.ACCEPTED
    }

    /**
     * Speaks the greeting as a normal SPEAKING session, so activation is refused
     * until it has finished (no timing hacks, no self-capture).
     */
    fun startupGreeting(): ActivationResult {
        if (closed || state != EdithState.STANDBY) return ActivationResult.BUSY
        speak(nextSession(), core.greet())
        return ActivationResult.ACCEPTED
    }

    /** Aborts everything and ignores any later callback. The loop cannot be reused. */
    fun shutdown() {
        if (closed) return
        closed = true
        nextSession()
        cancelWatchdog()
        contain("stop listening") { voiceInput.stopListening() }
        contain("stop speaking") { voiceOutput.stop() }
        state = EdithState.STANDBY
    }

    // ---- transitions -----------------------------------------------------------------

    private fun onTranscript(id: Long, text: String) {
        cancelWatchdog()
        setState(EdithState.PROCESSING)
        if (!isCurrent(id, EdithState.PROCESSING)) return

        val response = try {
            core.processCommand(text).spokenResponse
        } catch (e: Exception) {
            // Only the exception type is reported: its message could contain the user's words.
            diagnostic("Command processing threw ${e.javaClass.simpleName}")
            core.identity.internalErrorResponse()
        }
        if (!isCurrent(id, EdithState.PROCESSING)) return
        speak(id, response)
    }

    private fun handleInputError(id: Long, error: VoiceError) {
        cancelWatchdog()
        notifyListener { listener.onInputError(error) }
        speak(id, VoiceErrorPhrases.spokenFor(error, core.identity))
    }

    private fun onListenTimeout(id: Long) {
        if (!isCurrent(id, EdithState.LISTENING)) return
        diagnostic("Listening timed out")
        val newId = nextSession() // late recognizer callbacks are now stale
        contain("stop listening") { voiceInput.stopListening() }
        handleInputError(newId, VoiceError.SPEECH_TIMEOUT)
    }

    private fun speak(id: Long, text: String) {
        if (closed) return
        setState(EdithState.SPEAKING)
        if (!isCurrent(id, EdithState.SPEAKING)) return
        armWatchdog(config.speakTimeoutFor(text)) { onSpeakTimeout(id) }

        try {
            voiceOutput.speak(text) { outcome ->
                if (isCurrent(id, EdithState.SPEAKING)) onSpeechFinished(outcome)
            }
        } catch (e: Exception) {
            diagnostic("Voice output threw ${e.javaClass.simpleName}")
            if (isCurrent(id, EdithState.SPEAKING)) onSpeechFinished(SpeechOutcome.FAILED)
        }
    }

    private fun onSpeechFinished(outcome: SpeechOutcome) {
        cancelWatchdog()
        if (outcome == SpeechOutcome.FAILED) {
            notifyListener { listener.onOutputFailure() }
        }
        setState(EdithState.STANDBY)
    }

    private fun onSpeakTimeout(id: Long) {
        if (!isCurrent(id, EdithState.SPEAKING)) return
        diagnostic("Speaking timed out")
        nextSession() // a late completion callback is now stale
        contain("stop speaking") { voiceOutput.stop() }
        notifyListener { listener.onOutputFailure() }
        setState(EdithState.STANDBY)
    }

    // ---- helpers ---------------------------------------------------------------------

    private fun nextSession(): Long {
        session += 1
        return session
    }

    private fun isCurrent(id: Long, expected: EdithState): Boolean =
        !closed && id == session && state == expected

    private fun setState(newState: EdithState) {
        if (state == newState) return
        state = newState
        notifyListener { listener.onStateChanged(newState) }
    }

    private fun armWatchdog(delayMs: Long, task: () -> Unit) {
        cancelWatchdog()
        watchdog = scheduler.schedule(delayMs) {
            watchdog = null
            task()
        }
    }

    private fun cancelWatchdog() {
        watchdog?.cancel()
        watchdog = null
    }

    private fun notifyListener(call: () -> Unit) {
        try {
            call()
        } catch (e: Exception) {
            diagnostic("Listener threw ${e.javaClass.simpleName}")
        }
    }

    private fun contain(what: String, call: () -> Unit) {
        try {
            call()
        } catch (e: Exception) {
            diagnostic("Failed to $what: ${e.javaClass.simpleName}")
        }
    }

    private fun diagnostic(message: String) {
        try {
            listener.onDiagnostic(message)
        } catch (ignored: Exception) {
            // Last resort: a failing diagnostics sink must never break the state machine.
        }
    }
}
