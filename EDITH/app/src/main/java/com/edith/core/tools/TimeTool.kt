package com.edith.core.tools

import com.edith.core.CommandResult
import com.edith.core.EdithIdentity
import com.edith.core.Tool
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * EDITH's first tool: returns the current local time.
 *
 * Uses [java.time.LocalTime] — no Android framework dependency.
 * The time source can be overridden for testing via the [timeProvider] parameter.
 */
class TimeTool(
    private val identity: EdithIdentity,
    private val timeProvider: () -> LocalTime = { LocalTime.now() }
) : Tool {

    override val name: String = "time"
    override val description: String = "Returns the current local time"

    private val timePatterns = listOf(
        "what time",
        "current time",
        "tell me the time",
        "what's the time",
        "whats the time",
        "time is it",
        "do you have the time",
        "got the time"
    )

    override fun canHandle(input: String): Boolean {
        val lower = input.trim().lowercase()
        return timePatterns.any { lower.contains(it) }
    }

    override fun execute(input: String): CommandResult {
        val now = timeProvider()
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        val timeString = now.format(formatter)

        return CommandResult(
            success = true,
            spokenResponse = identity.formatResponse("It is $timeString"),
            data = mapOf(
                "time" to timeString,
                "hour" to now.hour.toString(),
                "minute" to now.minute.toString()
            )
        )
    }
}
