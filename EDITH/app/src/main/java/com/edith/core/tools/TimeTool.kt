package com.edith.core.tools

import com.edith.core.CommandResult
import com.edith.core.EdithIdentity
import com.edith.core.InputNormalizer
import com.edith.core.Tool
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * EDITH's first tool: returns the current local time.
 *
 * Uses [java.time.LocalTime] — no Android framework dependency.
 * The time source can be overridden for testing via the [timeProvider] parameter.
 *
 * Matching is deliberately strict: the whole (normalized) utterance must be a
 * request for the current time. Substring matching is NOT used, so questions
 * such as "what time does the store close" or "what time is my meeting" are
 * left for other tools (or answered as unknown) instead of being answered with
 * the current time.
 */
class TimeTool(
    private val identity: EdithIdentity,
    private val timeProvider: () -> LocalTime = { LocalTime.now() }
) : Tool {

    override val name: String = "time"
    override val description: String = "Returns the current local time"

    // Applied to InputNormalizer output: lowercase, no punctuation, apostrophes removed.
    private val timeRequests = listOf(
        Regex("^what time is it(?: right now| now| currently)?$"),
        Regex("^(?:what is|whats) the (?:current )?time(?: right now| now)?$"),
        Regex("^(?:tell|give) me the (?:current )?time(?: right now| now)?$"),
        Regex("^tell me what time it is(?: right now| now)?$"),
        Regex("^(?:do you have|have you got|got) the time$"),
        Regex("^(?:the )?current time$")
    )

    override fun canHandle(input: String): Boolean {
        val normalized = InputNormalizer.normalize(input)
        return timeRequests.any { it.matches(normalized) }
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
