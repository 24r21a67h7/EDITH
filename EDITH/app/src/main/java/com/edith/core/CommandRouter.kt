package com.edith.core

/**
 * Routes user input to the appropriate [Tool].
 *
 * The router normalizes the input with [InputNormalizer], then iterates through
 * registered tools in order and delegates to the first tool that reports it can
 * handle the input. If no tool matches, a graceful "unknown command" response is
 * returned using the EDITH identity.
 *
 * A tool that throws never propagates the exception to the caller: the router
 * returns a spoken internal-error result instead, so the runtime loop cannot be
 * wedged by a faulty tool. (Only the exception is contained here; nothing about
 * the user's input is logged.)
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
        val normalized = InputNormalizer.normalize(input)

        if (normalized.isEmpty()) {
            return CommandResult(
                success = false,
                spokenResponse = identity.unknownCommandResponse()
            )
        }

        return try {
            val tool = tools.firstOrNull { it.canHandle(normalized) }
            tool?.execute(input)
                ?: CommandResult(
                    success = false,
                    spokenResponse = identity.unknownCommandResponse()
                )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                spokenResponse = identity.internalErrorResponse()
            )
        }
    }

    /**
     * Returns the list of registered tool names (useful for diagnostics).
     */
    fun registeredTools(): List<String> = tools.map { it.name }
}
