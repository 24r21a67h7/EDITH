package com.edith.runtime

import com.edith.core.EdithIdentity
import com.edith.voice.VoiceError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceErrorPhrasesTest {

    private val identity = EdithIdentity()

    @Test
    fun `every error has a distinct spoken phrase that addresses Boss`() {
        val phrases = VoiceError.values().map { VoiceErrorPhrases.spokenFor(it, identity) }
        for ((error, phrase) in VoiceError.values().zip(phrases)) {
            assertTrue("$error", phrase.isNotBlank())
            assertTrue("$error should address the user", phrase.contains("Boss"))
        }
        assertEquals("phrases must be distinct", phrases.size, phrases.toSet().size)
    }

    @Test
    fun `setup problems tell the user where to fix them`() {
        for (error in listOf(
            VoiceError.RECOGNIZER_UNAVAILABLE,
            VoiceError.LANGUAGE_PACK_MISSING,
            VoiceError.PERMISSION_DENIED
        )) {
            assertTrue("$error", VoiceErrorPhrases.spokenFor(error, identity).contains("open EDITH", ignoreCase = true))
        }
    }

    @Test
    fun `network errors say EDITH will not use the network`() {
        val phrase = VoiceErrorPhrases.spokenFor(VoiceError.NETWORK_REQUIRED, identity)
        assertTrue(phrase.contains("won't use", ignoreCase = true))
        assertNotEquals(VoiceErrorPhrases.spokenFor(VoiceError.OTHER, identity), phrase)
    }

    @Test
    fun `uses the configured title`() {
        assertTrue(
            VoiceErrorPhrases.spokenFor(VoiceError.NO_MATCH, EdithIdentity(userTitle = "Sir")).contains("Sir")
        )
    }
}
