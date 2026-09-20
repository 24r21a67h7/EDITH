package com.edith.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [EdithCore].
 *
 * Verifies:
 * - Full pipeline: input → route → result
 * - Identity is wired correctly
 * - Time command works end-to-end through the core
 * - Unknown commands handled gracefully
 * - Greeting works
 * - Available tools reported correctly
 */
class EdithCoreTest {

    private lateinit var core: EdithCore

    @Before
    fun setUp() {
        core = EdithCore()
    }

    @Test
    fun `identity is EDITH`() {
        assertEquals("EDITH", core.identity.assistantName)
    }

    @Test
    fun `user title is Boss`() {
        assertEquals("Boss", core.identity.userTitle)
    }

    @Test
    fun `processCommand handles time query`() {
        val result = core.processCommand("What time is it?")
        assertTrue("Time query should succeed", result.success)
        assertTrue("Response should contain Boss", result.spokenResponse.contains("Boss"))
        assertTrue("Response should start with It is", result.spokenResponse.startsWith("It is"))
    }

    @Test
    fun `processCommand handles unknown command`() {
        val result = core.processCommand("What is the meaning of life?")
        assertFalse("Unknown command should not succeed", result.success)
        assertTrue("Response should contain Boss", result.spokenResponse.contains("Boss"))
    }

    @Test
    fun `processCommand handles empty input`() {
        val result = core.processCommand("")
        assertFalse("Empty input should not succeed", result.success)
    }

    @Test
    fun `processCommand handles whitespace only input`() {
        val result = core.processCommand("   \t  ")
        assertFalse("Whitespace input should not succeed", result.success)
    }

    @Test
    fun `greet returns valid greeting`() {
        val greeting = core.greet()
        assertTrue("Greeting should contain EDITH", greeting.contains("EDITH"))
        assertTrue("Greeting should contain Boss", greeting.contains("Boss"))
    }

    @Test
    fun `availableTools includes time tool`() {
        val tools = core.availableTools()
        assertTrue("Should have time tool", tools.contains("time"))
    }

    @Test
    fun `time response format is spoken naturally`() {
        val result = core.processCommand("tell me the time")
        assertTrue(result.success)
        // Should match pattern: "It is H:MM AM/PM, Boss."
        assertTrue(
            "Response should match expected format",
            result.spokenResponse.matches(Regex("It is \\d{1,2}:\\d{2} [AP]M, Boss\\."))
        )
    }
}
