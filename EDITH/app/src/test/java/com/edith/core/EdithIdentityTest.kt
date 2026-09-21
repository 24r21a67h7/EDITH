package com.edith.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [EdithIdentity].
 *
 * Verifies:
 * - Assistant name is "EDITH"
 * - User title is "Boss"
 * - Response formatting appends "Boss" correctly
 * - Greeting includes identity
 * - Unknown command response includes "Boss"
 */
class EdithIdentityTest {

    private val identity = EdithIdentity()

    @Test
    fun `assistant name is EDITH`() {
        assertEquals("EDITH", identity.assistantName)
    }

    @Test
    fun `user title is Boss`() {
        assertEquals("Boss", identity.userTitle)
    }

    @Test
    fun `formatResponse appends Boss with period`() {
        val result = identity.formatResponse("It is 3:42 PM")
        assertEquals("It is 3:42 PM, Boss.", result)
    }

    @Test
    fun `formatResponse works with empty message`() {
        val result = identity.formatResponse("")
        assertEquals(", Boss.", result)
    }

    @Test
    fun `greet includes assistant name and user title`() {
        val greeting = identity.greet()
        assertTrue("Greeting should contain EDITH", greeting.contains("EDITH"))
        assertTrue("Greeting should contain Boss", greeting.contains("Boss"))
    }

    @Test
    fun `unknownCommandResponse includes user title`() {
        val response = identity.unknownCommandResponse()
        assertTrue("Unknown command response should contain Boss", response.contains("Boss"))
    }

    @Test
    fun `custom identity preserves values`() {
        val custom = EdithIdentity(assistantName = "JARVIS", userTitle = "Sir")
        assertEquals("JARVIS", custom.assistantName)
        assertEquals("Sir", custom.userTitle)
        assertEquals("Hello, Sir.", custom.formatResponse("Hello"))
    }


    @Test
    fun `internal error response addresses the user`() {
        assertEquals("Something went wrong on my end, Boss.", identity.internalErrorResponse())
        assertTrue(EdithIdentity(userTitle = "Sir").internalErrorResponse().contains("Sir"))
    }
}
