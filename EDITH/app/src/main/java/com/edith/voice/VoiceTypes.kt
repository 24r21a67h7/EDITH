package com.edith.voice

/**
 * Why a listening session produced no usable transcript.
 *
 * Typed (instead of free-form strings) so the runtime can decide, deterministically,
 * what to say to the user. No variant ever carries user speech.
 */
enum class VoiceError {
    /** Audio was captured but nothing was recognized. */
    NO_MATCH,

    /** The user did not say anything (or the listening deadline passed). */
    SPEECH_TIMEOUT,

    /** No on-device recognizer is available, or it disconnected. */
    RECOGNIZER_UNAVAILABLE,

    /** The on-device recognizer exists but the required language pack is not installed. */
    LANGUAGE_PACK_MISSING,

    /** RECORD_AUDIO is not granted. */
    PERMISSION_DENIED,

    /** The microphone / audio pipeline failed. */
    AUDIO_ERROR,

    /** The recognizer is busy or rate-limiting requests. */
    RECOGNIZER_BUSY,

    /**
     * The recognizer reported a network error. On-device recognition should never
     * need the network; this means offline operation is NOT working.
     */
    NETWORK_REQUIRED,

    /** Anything else. */
    OTHER
}

/** How a single [VoiceOutput.speak] call ended. */
enum class SpeechOutcome {
    /** The whole utterance was spoken. */
    COMPLETED,

    /** Speech was stopped or replaced before it finished. */
    INTERRUPTED,

    /** Speech could not be produced (engine/voice unavailable, synthesis error). */
    FAILED
}

/** Whether text-to-speech can be used, honoring the offline-first requirement. */
enum class VoiceOutputStatus {
    NOT_INITIALIZED,
    INITIALIZING,

    /** An installed, non-network voice was selected. */
    READY,

    /** No text-to-speech engine could be started. */
    ENGINE_UNAVAILABLE,

    /**
     * The engine works, but no installed offline English voice could be found (or
     * confirmed). EDITH does not fall back to a possibly network-backed voice.
     */
    NO_OFFLINE_VOICE
}

/** Result of the first-run check for on-device speech recognition. */
enum class SttReadiness {
    READY,
    RECOGNIZER_UNAVAILABLE,
    LANGUAGE_PACK_MISSING,

    /** The check itself failed; recognition may still work. */
    CHECK_FAILED
}

/** Picks the on-device recognition language from what is actually installed. */
object LanguageSelection {

    /**
     * Prefers `en-US`, otherwise the first installed English variant (e.g. `en-IN`).
     * Returns null if no English pack is installed.
     */
    fun pickEnglish(installedTags: List<String>): String? {
        val tags = installedTags.map { it.replace('_', '-') }
        return tags.firstOrNull { it.equals("en-US", ignoreCase = true) }
            ?: tags.firstOrNull { it.substringBefore('-').equals("en", ignoreCase = true) }
    }
}
