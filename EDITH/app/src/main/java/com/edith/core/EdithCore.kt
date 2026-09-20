package com.edith.core

import com.edith.core.tools.TimeTool

/**
 * EDITH Core — the central brain of the assistant.
 *
 * Owns the [EdithIdentity], creates and wires the [CommandRouter] with all
 * registered [Tool] instances, and provides the single entry point for
 * processing user commands.
 *
 * This class has zero Android dependencies. It can be tested with pure JUnit.
 */
class EdithCore(
    val identity: EdithIdentity = EdithIdentity()
) {

    private val tools: List<Tool> = listOf(
        TimeTool(identity)
    )

    private val router: CommandRouter = CommandRouter(tools, identity)

    /**
     * Processes a raw user command string and returns a result.
     *
     * This is the main entry point for the EDITH runtime loop:
     * Voice Input → [processCommand] → Voice Output
     *
     * @param rawInput The transcribed user speech or text input.
     * @return The [CommandResult] to be spoken/displayed.
     */
    fun processCommand(rawInput: String): CommandResult {
        return router.route(rawInput)
    }

    /**
     * Returns a greeting suitable for when EDITH first comes online.
     */
    fun greet(): String {
        return identity.greet()
    }

    /**
     * Returns the list of registered tool names (for diagnostics).
     */
    fun availableTools(): List<String> = router.registeredTools()
}
