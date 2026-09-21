package com.edith.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InputNormalizerTest {

    @Test
    fun `lowercases trims and collapses whitespace`() {
        assertEquals("what time is it", InputNormalizer.normalize("  What   TIME\tis it  "))
    }

    @Test
    fun `removes punctuation and apostrophes`() {
        assertEquals("whats the time", InputNormalizer.normalize("What's the time?!"))
        assertEquals("whats the time", InputNormalizer.normalize("What\u2019s the time"))
        assertEquals("what time is it", InputNormalizer.normalize("what, time... is it?"))
    }

    @Test
    fun `strips leading addressing`() {
        assertEquals("what time is it", InputNormalizer.normalize("Hey EDITH, what time is it"))
        assertEquals("what time is it", InputNormalizer.normalize("ok edith what time is it"))
        assertEquals("what time is it", InputNormalizer.normalize("Edith, what time is it"))
    }

    @Test
    fun `strips politeness in any order`() {
        assertEquals("tell me the time", InputNormalizer.normalize("Could you please tell me the time"))
        assertEquals("tell me the time", InputNormalizer.normalize("please can you tell me the time"))
        assertEquals("tell me the time", InputNormalizer.normalize("hey edith can you please tell me the time"))
    }

    @Test
    fun `strips trailing please edith boss`() {
        assertEquals("what time is it", InputNormalizer.normalize("what time is it please"))
        assertEquals("what time is it", InputNormalizer.normalize("what time is it, Boss"))
        assertEquals("what time is it", InputNormalizer.normalize("what time is it edith please"))
    }

    @Test
    fun `does not strip words inside sentences`() {
        assertEquals("edithing is not a word", InputNormalizer.normalize("Edithing is not a word"))
        assertEquals("i need a pleasant surprise", InputNormalizer.normalize("I need a pleasant surprise"))
    }

    @Test
    fun `input that is only fillers becomes empty`() {
        assertEquals("", InputNormalizer.normalize("hey edith"))
        assertEquals("", InputNormalizer.normalize("  ...  "))
        assertEquals("", InputNormalizer.normalize(""))
    }

    @Test
    fun `normalization is idempotent`() {
        val inputs = listOf("Hey EDITH, could you please tell me what time it is, Boss?", "What's up", "", "   ")
        for (input in inputs) {
            val once = InputNormalizer.normalize(input)
            assertEquals(once, InputNormalizer.normalize(once))
        }
    }
}
