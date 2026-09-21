package com.edith.core

import com.edith.core.tools.TimeTool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [CommandRouter].
 *
 * Verifies:
 * - Routes time commands to TimeTool
 * - Unknown commands return graceful fallback
 * - Empty/blank input handled safely
 * - Tool registration is correct
 */
class CommandRouterTest {

    private lateinit var identity: EdithIdentity
    private lateinit var router: CommandRouter

    @Before
    fun setUp() {
        identity = EdithIdentity()
        val tools = listOf(TimeTool(identity))
        router = CommandRouter(tools, identity)
    }

    @Test
    fun `routes time query to TimeTool`() {
        val result = router.route("What time is it?")
        assertTrue("Time query should succeed", result.success)
        assertTrue("Response should contain Boss", result.spokenResponse.contains("Boss"))
    }

    @Test
    fun `routes time query case insensitive`() {
        val result = router.route("WHAT TIME IS IT")
        assertTrue("Uppercase time query should succeed", result.success)
    }

    @Test
    fun `routes time query with extra whitespace`() {
        val result = router.route("  what time is it  ")
        assertTrue("Padded time query should succeed", result.success)
    }

    @Test
    fun `unknown command returns failure`() {
        val result = router.route("launch the missiles")
        assertFalse("Unknown command should not succeed", result.success)
        assertTrue("Unknown response should contain Boss", result.spokenResponse.contains("Boss"))
    }

    @Test
    fun `empty input returns failure`() {
        val result = router.route("")
        assertFalse("Empty input should not succeed", result.success)
    }

    @Test
    fun `blank input returns failure`() {
        val result = router.route("   ")
        assertFalse("Blank input should not succeed", result.success)
    }

    @Test
    fun `registered tools list is correct`() {
        val tools = router.registeredTools()
        assertEquals(1, tools.size)
        assertEquals("time", tools[0])
    }


    // --- Phase 2: routing must be exact, and exception-safe ---

    @Test
    fun `does not answer with the time for other what-time questions`() {
        val questions = listOf(
            "what time does the store close?",
            "what time is my meeting?",
            "what time should I wake up?"
        )
        for (q in questions) {
            val result = router.route(q)
            assertFalse("'$q' must not succeed as a time request", result.success)
            assertEquals(identity.unknownCommandResponse(), result.spokenResponse)
        }
    }

    @Test
    fun `strips addressing and politeness before routing`() {
        val result = router.route("Hey EDITH, could you please tell me what time it is?")
        assertTrue(result.success)
        assertTrue(result.spokenResponse.endsWith(", Boss."))
    }

    @Test
    fun `input that is only addressing is unknown`() {
        val result = router.route("hey edith")
        assertFalse(result.success)
        assertEquals(identity.unknownCommandResponse(), result.spokenResponse)
    }

    private class ThrowingTool(private val throwInCanHandle: Boolean) : Tool {
        override val name = "throwing"
        override val description = "always fails"
        override fun canHandle(input: String): Boolean {
            if (throwInCanHandle) throw IllegalStateException("boom: $input")
            return true
        }
        override fun execute(input: String): CommandResult = throw IllegalStateException("boom: $input")
    }

    @Test
    fun `a tool that throws in execute yields a spoken internal error`() {
        val r = CommandRouter(listOf(ThrowingTool(throwInCanHandle = false)), identity)
        val result = r.route("anything at all")
        assertFalse(result.success)
        assertEquals(identity.internalErrorResponse(), result.spokenResponse)
    }

    @Test
    fun `a tool that throws in canHandle yields a spoken internal error`() {
        val r = CommandRouter(listOf(ThrowingTool(throwInCanHandle = true)), identity)
        val result = r.route("anything at all")
        assertFalse(result.success)
        assertEquals(identity.internalErrorResponse(), result.spokenResponse)
    }
}
