package com.edith.runtime

import com.edith.voice.SpeechOutcome
import com.edith.voice.VoiceError
import com.edith.voice.VoiceInput
import com.edith.voice.VoiceOutput
import com.edith.voice.VoiceOutputStatus

/** Virtual-time scheduler: nothing runs until [advance] is called. */
class FakeScheduler : Scheduler {
    private class Task(val at: Long, val run: () -> Unit) { var cancelled = false }

    private var now = 0L
    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val t = Task(now + delayMs, task)
        tasks += t
        return Cancellable { t.cancelled = true }
    }

    fun advance(ms: Long) {
        val target = now + ms
        while (true) {
            val next = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
            tasks.remove(next)
            now = next.at
            next.run()
        }
        now = target
    }

    fun pendingCount(): Int = tasks.count { !it.cancelled }
}

class FakeVoiceInput : VoiceInput {
    var startCalls = 0
    var stopCalls = 0
    var throwOnStart = false
    var onStartHook: ((onResult: (String) -> Unit, onError: (VoiceError) -> Unit) -> Unit)? = null
    var lastOnResult: ((String) -> Unit)? = null
    var lastOnError: ((VoiceError) -> Unit)? = null

    override fun startListening(onResult: (String) -> Unit, onError: (VoiceError) -> Unit) {
        startCalls++
        if (throwOnStart) throw IllegalStateException("recognizer exploded")
        lastOnResult = onResult
        lastOnError = onError
        onStartHook?.invoke(onResult, onError)
    }

    override fun stopListening() { stopCalls++ }
    override fun isAvailable() = true
    override fun destroy() {}
}

class FakeVoiceOutput : VoiceOutput {
    override var status: VoiceOutputStatus = VoiceOutputStatus.READY
    val spoken = mutableListOf<String>()
    var stopCalls = 0
    var throwOnSpeak = false
    var onSpeakHook: ((text: String, onFinished: (SpeechOutcome) -> Unit) -> Unit)? = null
    var lastOnFinished: ((SpeechOutcome) -> Unit)? = null

    override fun speak(text: String, onFinished: (SpeechOutcome) -> Unit) {
        spoken += text
        if (throwOnSpeak) throw IllegalStateException("tts exploded")
        lastOnFinished = onFinished
        onSpeakHook?.invoke(text, onFinished)
    }

    override fun stop() { stopCalls++ }
    override fun destroy() {}
}

class RecordingListener : LoopListener {
    val states = mutableListOf<EdithState>()
    val inputErrors = mutableListOf<VoiceError>()
    var outputFailures = 0
    val diagnostics = mutableListOf<String>()

    override fun onStateChanged(state: EdithState) { states += state }
    override fun onInputError(error: VoiceError) { inputErrors += error }
    override fun onOutputFailure() { outputFailures++ }
    override fun onDiagnostic(message: String) { diagnostics += message }
}
