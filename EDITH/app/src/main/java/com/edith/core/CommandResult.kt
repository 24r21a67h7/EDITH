package com.edith.core

/**
 * Result of a command processed by EDITH.
 *
 * @property success Whether the command was handled successfully.
 * @property spokenResponse The text to be spoken aloud by the voice output layer.
 * @property data Optional structured data from the tool (for UI display or further processing).
 */
data class CommandResult(
    val success: Boolean,
    val spokenResponse: String,
    val data: Map<String, String>? = null
)
