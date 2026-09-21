package com.edith.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageSelectionTest {

    @Test
    fun `prefers en-US`() {
        assertEquals("en-US", LanguageSelection.pickEnglish(listOf("en-GB", "en-US", "hi-IN")))
    }

    @Test
    fun `falls back to another installed English variant`() {
        assertEquals("en-IN", LanguageSelection.pickEnglish(listOf("hi-IN", "en-IN")))
    }

    @Test
    fun `normalizes underscores and case`() {
        assertEquals("en-US", LanguageSelection.pickEnglish(listOf("en_US")))
        assertEquals("EN-gb", LanguageSelection.pickEnglish(listOf("EN-gb")))
    }

    @Test
    fun `returns null when no English pack is installed`() {
        assertNull(LanguageSelection.pickEnglish(emptyList()))
        assertNull(LanguageSelection.pickEnglish(listOf("hi-IN", "fr-FR", "enm-XX")))
    }
}
