package com.edith.runtime

import com.edith.core.EdithIdentity
import com.edith.voice.VoiceError

/**
 * What EDITH says when voice input fails. Pure Kotlin, contains no user speech.
 *
 * Setup problems tell the user to open EDITH (the on-screen setup checklist
 * has the fix); transient problems just ask them to try again.
 */
object VoiceErrorPhrases {

    fun spokenFor(error: VoiceError, identity: EdithIdentity): String {
        val boss = identity.userTitle
        return when (error) {
            VoiceError.NO_MATCH ->
                "I didn't catch that, $boss."
            VoiceError.SPEECH_TIMEOUT ->
                "I didn't hear anything, $boss."
            VoiceError.RECOGNIZER_UNAVAILABLE ->
                "On-device speech recognition isn't available, $boss. Please open EDITH to check the setup."
            VoiceError.LANGUAGE_PACK_MISSING ->
                "The offline English speech pack isn't installed, $boss. Please open EDITH to fix that."
            VoiceError.PERMISSION_DENIED ->
                "I need microphone permission, $boss. Please open EDITH to grant it."
            VoiceError.AUDIO_ERROR ->
                "I had trouble using the microphone, $boss."
            VoiceError.RECOGNIZER_BUSY ->
                "Speech recognition is busy, $boss. Please try again in a moment."
            VoiceError.NETWORK_REQUIRED ->
                "The speech recognizer asked for the network, $boss. I won't use it. Please check the offline speech pack."
            VoiceError.OTHER ->
                "Something went wrong with speech recognition, $boss."
        }
    }
}
