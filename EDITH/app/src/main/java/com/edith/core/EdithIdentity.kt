package com.edith.core

/**
 * EDITH Identity Configuration.
 *
 * Single source of truth for the assistant's identity and the user's designation.
 * No Android dependencies — this is pure Kotlin.
 */
data class EdithIdentity(
    val assistantName: String = "EDITH",
    val userTitle: String = "Boss"
) {
    /**
     * Formats a response message with the user's title appended.
     * Example: formatResponse("It is 3:42 PM") → "It is 3:42 PM, Boss."
     */
    fun formatResponse(message: String): String {
        return "$message, $userTitle."
    }

    /**
     * Returns a greeting message for when EDITH comes online.
     */
    fun greet(): String {
        return "$assistantName online. Ready when you are, $userTitle."
    }

    /**
     * Returns a polite fallback response when a command is not recognized.
     */
    fun unknownCommandResponse(): String {
        return "I'm not sure how to handle that yet, $userTitle. My capabilities are still limited."
    }

    /**
     * Spoken when an internal component fails unexpectedly (e.g. a tool throws).
     */
    fun internalErrorResponse(): String {
        return "Something went wrong on my end, $userTitle."
    }
}
