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
}
