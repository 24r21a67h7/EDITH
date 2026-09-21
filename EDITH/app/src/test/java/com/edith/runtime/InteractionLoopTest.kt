package com.edith.runtime

import com.edith.core.CommandResult
import com.edith.core.EdithCore
import com.edith.core.Tool
import com.edith.voice.SpeechOutcome
import com.edith.voice.VoiceError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

class InteractionLoopTest {

    private lateinit var scheduler: FakeScheduler
    private lateinit var input: FakeVoiceInput
    private lateinit var output: FakeVoiceOutput
    private lateinit var listener: RecordingListener
    private lateinit var core: EdithCore
    private lateinit var loop: InteractionLoop
    private val config = LoopConfig()

    private fun build(core: EdithCore = EdithCore()) {
        this.core = core
        loop = InteractionLoop(core, input, output, scheduler, listener, config)
    }

    @Before
    fun setUp() {
        scheduler = FakeScheduler()
        input = FakeVoiceInput()
        output = FakeVoiceOutput()
        listener = RecordingListener()
        build()
    }

    private fun assertStandby() = assertEquals(EdithState.STANDBY, loop.state)

    // ---- happy path ------------------------------------------------------------------

    @Test
    fun `full push-to-talk cycle ends in standby with the Boss response`() {
        assertEquals(ActivationResult.ACCEPTED, loop.activate())
        assertEquals(EdithState.LISTENING, loop.state)

        input.lastOnResult!!("What time is it?")
        assertEquals(EdithState.SPEAKING, loop.state)
        assertEquals(1, output.spoken.size)
        assertTrue(output.spoken[0].matches(Regex("It is \\d{1,2}:\\d{2} [AP]M, Boss\\.")))

        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
        assertEquals(
            listOf(EdithState.LISTENING, EdithState.PROCESSING, EdithState.SPEAKING, EdithState.STANDBY),
            listener.states
        )
        assertEquals(0, scheduler.pendingCount())
    }

    @Test
    fun `unknown commands are spoken and return to standby`() {
        loop.activate()
        input.lastOnResult!!("what is the meaning of life")
        assertTrue(output.spoken[0].contains("not sure how to handle that"))
        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
    }

    // ---- activation gating (no mic while busy / speaking) ----------------------------

    @Test
    fun `activation is refused unless standby`() {
        assertEquals(ActivationResult.ACCEPTED, loop.activate())
        assertEquals(ActivationResult.BUSY, loop.activate()) // listening
        assertEquals(1, input.startCalls)

        input.lastOnResult!!("what time is it")
        assertEquals(ActivationResult.BUSY, loop.activate()) // speaking
        assertEquals(1, input.startCalls)
    }

    @Test
    fun `greeting is spoken as a normal speaking session and blocks activation until done`() {
        assertEquals(ActivationResult.ACCEPTED, loop.startupGreeting())
        assertEquals(EdithState.SPEAKING, loop.state)
        assertEquals(listOf(core.greet()), output.spoken)

        assertEquals("microphone must not open while the greeting plays", ActivationResult.BUSY, loop.activate())
        assertEquals(0, input.startCalls)

        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
        assertEquals(ActivationResult.ACCEPTED, loop.activate())
        assertEquals(1, input.startCalls)
    }

    @Test
    fun `microphone never opens while any speech is in progress`() {
        // Error message being spoken also counts as speaking.
        loop.activate()
        input.lastOnError!!(VoiceError.NO_MATCH)
        assertEquals(EdithState.SPEAKING, loop.state)
        assertEquals(ActivationResult.BUSY, loop.activate())
        assertEquals(1, input.startCalls)
    }

    // ---- listening timeout -----------------------------------------------------------

    @Test
    fun `a recognizer that never answers is timed out, aborted and reported`() {
        loop.activate()
        scheduler.advance(config.listenTimeoutMs - 1)
        assertEquals(EdithState.LISTENING, loop.state)

        scheduler.advance(1)
        assertEquals("recognizer aborted", 1, input.stopCalls)
        assertEquals(EdithState.SPEAKING, loop.state)
        assertEquals(listOf(VoiceError.SPEECH_TIMEOUT), listener.inputErrors)
        assertEquals("I didn't hear anything, Boss.", output.spoken.single())

        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
    }

    @Test
    fun `late recognizer result after a timeout is ignored`() {
        loop.activate()
        val staleResult = input.lastOnResult!!
        scheduler.advance(config.listenTimeoutMs)
        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()

        staleResult("what time is it")
        assertStandby()
        assertEquals("only the timeout message was ever spoken", 1, output.spoken.size)
    }

    // ---- speaking failures / interruption / watchdog ---------------------------------

    @Test
    fun `speech failure returns to standby and is reported`() {
        loop.activate()
        input.lastOnResult!!("what time is it")
        output.lastOnFinished!!(SpeechOutcome.FAILED)
        assertStandby()
        assertEquals(1, listener.outputFailures)
    }

    @Test
    fun `interrupted speech returns to standby without reporting a failure`() {
        loop.activate()
        input.lastOnResult!!("what time is it")
        output.lastOnFinished!!(SpeechOutcome.INTERRUPTED)
        assertStandby()
        assertEquals(0, listener.outputFailures)
    }

    @Test
    fun `tts that never calls back is stopped by the watchdog`() {
        loop.activate()
        input.lastOnResult!!("what time is it")
        val timeout = config.speakTimeoutFor(output.spoken[0])

        scheduler.advance(timeout - 1)
        assertEquals(EdithState.SPEAKING, loop.state)
        scheduler.advance(1)

        assertStandby()
        assertEquals("speech stopped", 1, output.stopCalls)
        assertEquals(1, listener.outputFailures)

        output.lastOnFinished!!(SpeechOutcome.COMPLETED) // late callback: harmless
        assertStandby()
    }

    @Test
    fun `tts that throws returns to standby`() {
        output.throwOnSpeak = true
        loop.activate()
        input.lastOnResult!!("what time is it")
        assertStandby()
        assertEquals(1, listener.outputFailures)
        assertTrue(listener.diagnostics.any { it.contains("IllegalStateException") })
    }

    @Test
    fun `tts that finishes synchronously is handled`() {
        output.onSpeakHook = { _, done -> done(SpeechOutcome.COMPLETED) }
        loop.activate()
        input.lastOnResult!!("what time is it")
        assertStandby()
        assertEquals(0, scheduler.pendingCount())
    }

    @Test
    fun `duplicate speech completions are ignored`() {
        loop.activate()
        input.lastOnResult!!("what time is it")
        val done = output.lastOnFinished!!
        done(SpeechOutcome.COMPLETED)
        assertEquals(ActivationResult.ACCEPTED, loop.activate())
        done(SpeechOutcome.COMPLETED) // stale duplicate must not end the NEW listening session
        assertEquals(EdithState.LISTENING, loop.state)
    }

    // ---- input errors ----------------------------------------------------------------

    @Test
    fun `every voice error is spoken then returns to standby`() {
        for (error in VoiceError.values()) {
            setUp()
            loop.activate()
            input.lastOnError!!(error)
            assertEquals("$error", EdithState.SPEAKING, loop.state)
            assertEquals(VoiceErrorPhrases.spokenFor(error, core.identity), output.spoken.single())
            assertEquals(listOf(error), listener.inputErrors)
            output.lastOnFinished!!(SpeechOutcome.COMPLETED)
            assertStandby()
        }
    }

    @Test
    fun `synchronous recognizer error is handled`() {
        input.onStartHook = { _, onError -> onError(VoiceError.LANGUAGE_PACK_MISSING) }
        assertEquals(ActivationResult.ACCEPTED, loop.activate())
        assertEquals(EdithState.SPEAKING, loop.state)
        assertTrue(output.spoken.single().contains("speech pack"))
        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
    }

    @Test
    fun `recognizer that throws is reported and returns to standby`() {
        input.throwOnStart = true
        loop.activate()
        assertEquals(listOf(VoiceError.OTHER), listener.inputErrors)
        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
    }

    @Test
    fun `an error message that cannot be spoken does not loop or stick`() {
        output.throwOnSpeak = true
        loop.activate()
        input.lastOnError!!(VoiceError.NO_MATCH)
        assertStandby()
        assertEquals(1, output.spoken.size)
    }

    @Test
    fun `duplicate recognizer callbacks are ignored`() {
        loop.activate()
        val onResult = input.lastOnResult!!
        val onError = input.lastOnError!!
        onResult("what time is it")
        onResult("what time is it")
        onError(VoiceError.NO_MATCH)
        assertEquals(1, output.spoken.size)
    }

    // ---- core failures ---------------------------------------------------------------

    @Test
    fun `a throwing tool is spoken as an internal error and does not stick`() {
        val faulty = object : Tool {
            override val name = "faulty"
            override val description = "throws"
            override fun canHandle(input: String) = true
            override fun execute(input: String): CommandResult = throw IllegalStateException("boom: $input")
        }
        build(EdithCore(tools = listOf(faulty)))
        loop.activate()
        input.lastOnResult!!("my secret password is hunter2")
        assertEquals(core.identity.internalErrorResponse(), output.spoken.single())
        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
    }

    @Test
    fun `a core that throws outright is contained by the loop`() {
        val exploding = object : EdithCore() {
            override fun processCommand(rawInput: String): CommandResult = throw RuntimeException("secret: $rawInput")
        }
        build(exploding)
        loop.activate()
        input.lastOnResult!!("my secret password is hunter2")
        assertEquals(exploding.identity.internalErrorResponse(), output.spoken.single())
        output.lastOnFinished!!(SpeechOutcome.COMPLETED)
        assertStandby()
    }

    // ---- privacy ---------------------------------------------------------------------

    @Test
    fun `diagnostics never contain what the user said`() {
        val exploding = object : EdithCore() {
            override fun processCommand(rawInput: String): CommandResult = throw RuntimeException("leak: $rawInput")
        }
        build(exploding)
        output.throwOnSpeak = true
        loop.activate()
        input.lastOnResult!!("my secret password is hunter2")
        assertTrue(listener.diagnostics.isNotEmpty())
        for (line in listener.diagnostics) {
            assertFalse(line, line.contains("secret") || line.contains("hunter2") || line.contains("leak"))
        }
    }

    // ---- shutdown --------------------------------------------------------------------

    @Test
    fun `shutdown aborts everything and ignores later callbacks`() {
        loop.activate()
        val onResult = input.lastOnResult!!
        loop.shutdown()
        assertStandby()
        assertEquals(1, input.stopCalls)
        assertEquals(1, output.stopCalls)
        assertEquals(0, scheduler.pendingCount())

        onResult("what time is it")
        assertTrue(output.spoken.isEmpty())
        assertEquals(ActivationResult.BUSY, loop.activate())
        assertEquals(ActivationResult.BUSY, loop.startupGreeting())
    }

    @Test
    fun `shutdown while speaking ignores the late completion`() {
        loop.activate()
        input.lastOnResult!!("what time is it")
        val done = output.lastOnFinished!!
        loop.shutdown()
        done(SpeechOutcome.FAILED)
        assertEquals(0, listener.outputFailures)
        assertStandby()
    }

    // ---- the guarantee ---------------------------------------------------------------

    /**
     * Chaos test: random (seeded) misbehavior from the recognizer and the TTS engine —
     * never answering, answering twice, throwing, answering synchronously — must ALWAYS end in
     * STANDBY once enough virtual time has passed, and must never leave a timer behind.
     */
    @Test
    fun `no misbehavior can leave EDITH stuck busy`() {
        val random = Random(20260921L)
        val worst = config.listenTimeoutMs + config.speakTimeoutFor("x".repeat(200)) + 1_000

        repeat(500) { round ->
            scheduler = FakeScheduler()
            input = FakeVoiceInput()
            output = FakeVoiceOutput()
            listener = RecordingListener()
            build()

            input.throwOnStart = random.nextInt(8) == 0
            input.onStartHook = { onResult, onError ->
                when (random.nextInt(6)) {
                    0 -> { /* never answers */ }
                    1 -> onResult("what time is it")
                    2 -> onError(VoiceError.values()[random.nextInt(VoiceError.values().size)])
                    3 -> { onResult("what time is it"); onResult("again") }
                    4 -> { onError(VoiceError.NO_MATCH); onResult("late") }
                    else -> { /* answers later, maybe */ }
                }
            }
            output.throwOnSpeak = random.nextInt(8) == 0
            output.onSpeakHook = { _, done ->
                when (random.nextInt(5)) {
                    0 -> { /* never finishes */ }
                    1 -> done(SpeechOutcome.COMPLETED)
                    2 -> done(SpeechOutcome.FAILED)
                    3 -> done(SpeechOutcome.INTERRUPTED)
                    else -> { done(SpeechOutcome.COMPLETED); done(SpeechOutcome.FAILED) }
                }
            }

            if (random.nextBoolean()) loop.startupGreeting()
            loop.activate()
            input.lastOnResult?.let { if (random.nextBoolean()) it("what time is it") }
            scheduler.advance(worst)

            assertEquals("round $round", EdithState.STANDBY, loop.state)
            assertEquals("round $round leaves no timers", 0, scheduler.pendingCount())
            assertEquals("round $round can be activated again", ActivationResult.ACCEPTED, loop.activate())
        }
    }
}
