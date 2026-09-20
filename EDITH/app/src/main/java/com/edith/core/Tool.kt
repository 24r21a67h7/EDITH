package com.edith.core

/**
 * Interface for an EDITH tool.
 *
 * A Tool is a single, focused capability that EDITH can invoke.
 * Tools are registered with the [CommandRouter] and matched against user input.
 *
 * Tools must be self-contained and should not depend on Android framework classes.
 * Android-specific functionality should be injected via interfaces/adapters.
 */
interface Tool {

    /** Unique name identifying this tool. */
    val name: String

    /** Human-readable description of what this tool does. */
    val description: String

    /**
     * Determines whether this tool can handle the given input.
     *
     * @param input Normalized (trimmed, lowercased) user input.
     * @return true if this tool should handle the input.
     */
    fun canHandle(input: String): Boolean

    /**
     * Executes the tool with the given input and returns a result.
     *
     * @param input The raw user input string.
     * @return The result of executing the tool.
     */
    fun execute(input: String): CommandResult
}
