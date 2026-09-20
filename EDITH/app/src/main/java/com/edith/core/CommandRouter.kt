package com.edith.core

/**
 * Routes user input to the appropriate [Tool].
 *
 * The router iterates through registered tools in order and delegates
 * to the first tool that reports it can handle the input. If no tool matches,
 * a graceful "unknown command" response is returned using the EDITH identity.
 */
class CommandRouter(
    private val tools: List<Tool>,
    private val identity: EdithIdentity
) {

    /**
     * Routes the given input to a matching tool and returns the result.
     *
     * @param input Raw user input (will be normalized internally).
     * @return The [CommandResult] from the matched tool, or a fallback response.
     */
    fun route(input: String): CommandResult {
        val normalized = input.trim().lowercase()

        if (normalized.isBlank()) {
            return CommandResult(
                success = false,
                spokenResponse = identity.unknownCommandResponse()
            )
        }

        for (tool in tools) {
            if (tool.canHandle(normalized)) {
                return tool.execute(input)
            }
        }

        return CommandResult(
            success = false,
            spokenResponse = identity.unknownCommandResponse()
        )
    }

    /**
     * Returns the list of registered tool names (useful for diagnostics).
     */
    fun registeredTools(): List<String> = tools.map { it.name }
}
