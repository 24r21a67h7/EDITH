package com.edith.core

import com.edith.core.tools.TimeTool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

/**
 * Tests for [TimeTool].
 *
 * Verifies:
 * - canHandle matches time-related phrases
 * - canHandle rejects unrelated phrases
 * - execute returns formatted time with "Boss"
 * - execute returns structured data
 * - Time formatting is correct
 */
class TimeToolTest {

    private lateinit var identity: EdithIdentity
    private lateinit var timeTool: TimeTool

    @Before
    fun setUp() {
        identity = EdithIdentity()
        // Inject a fixed time for deterministic testing
        timeTool = TimeTool(identity) { LocalTime.of(15, 42, 0) }
    }

    // --- canHandle tests ---

    @Test
    fun `canHandle matches what time is it`() {
        assertTrue(timeTool.canHandle("what time is it"))
    }

    @Test
    fun `canHandle matches what's the time`() {
        assertTrue(timeTool.canHandle("what's the time"))
    }

    @Test
    fun `canHandle matches tell me the time`() {
        assertTrue(timeTool.canHandle("tell me the time"))
    }

    @Test
    fun `canHandle matches current time`() {
        assertTrue(timeTool.canHandle("current time"))
    }

    @Test
    fun `canHandle matches do you have the time`() {
        assertTrue(timeTool.canHandle("do you have the time"))
    }

    @Test
    fun `canHandle is case insensitive`() {
        assertTrue(timeTool.canHandle("What Time Is It"))
    }

    @Test
    fun `canHandle rejects unrelated input`() {
        assertFalse(timeTool.canHandle("what is the weather"))
    }

    @Test
    fun `canHandle rejects empty input`() {
        assertFalse(timeTool.canHandle(""))
    }

    @Test
    fun `canHandle rejects random text`() {
        assertFalse(timeTool.canHandle("launch the missiles"))
    }

    // --- execute tests ---

    @Test
    fun `execute returns success`() {
        val result = timeTool.execute("what time is it")
        assertTrue(result.success)
    }

    @Test
    fun `execute response contains Boss`() {
        val result = timeTool.execute("what time is it")
        assertTrue("Response should contain Boss", result.spokenResponse.contains("Boss"))
    }

    @Test
    fun `execute response contains formatted time`() {
        val result = timeTool.execute("what time is it")
        assertTrue("Response should contain 3:42 PM", result.spokenResponse.contains("3:42 PM"))
    }

    @Test
    fun `execute response format is correct`() {
        val result = timeTool.execute("what time is it")
        assertEquals("It is 3:42 PM, Boss.", result.spokenResponse)
    }

    @Test
    fun `execute returns structured data`() {
        val result = timeTool.execute("what time is it")
        assertNotNull(result.data)
        assertEquals("3:42 PM", result.data!!["time"])
        assertEquals("15", result.data!!["hour"])
        assertEquals("42", result.data!!["minute"])
    }

    @Test
    fun `tool name is time`() {
        assertEquals("time", timeTool.name)
    }
}
