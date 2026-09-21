package com.edith.core

/**
 * Deterministic text normalization for recognized speech / typed input.
 *
 * Pure Kotlin, no Android dependencies. The result is safe to match against
 * with anchored patterns:
 *
 * - lowercased
 * - apostrophes removed ("what's" -> "whats")
 * - all other punctuation replaced by spaces, whitespace collapsed and trimmed
 * - leading addressing/politeness removed ("hey edith", "edith", "can you", "please" ...)
 * - trailing "please" / "edith" / "boss" removed
 *
 * Normalization is idempotent: `normalize(normalize(x)) == normalize(x)`.
 */
object InputNormalizer {

    private val apostrophes = Regex("['\u2018\u2019\u02BC`]")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}\\s]")
    private val whitespace = Regex("\\s+")

    // Leading fillers, stripped repeatedly until none remain (e.g. "hey edith can you please ...").
    private val leadingFillers = listOf(
        Regex("^(?:hey|ok|okay)\\s+edith(?:\\s+|$)"),
        Regex("^edith(?:\\s+|$)"),
        Regex("^(?:can|could|would|will)\\s+you(?:\\s+|$)"),
        Regex("^please(?:\\s+|$)")
    )

    private val trailingFillers = Regex("\\s+(?:please|edith|boss)$")

    fun normalize(raw: String): String {
        var text = raw.lowercase()
        text = apostrophes.replace(text, "")
        text = nonAlphanumeric.replace(text, " ")
        text = whitespace.replace(text, " ").trim()

        var changed = true
        while (changed && text.isNotEmpty()) {
            changed = false
            for (filler in leadingFillers) {
                val stripped = filler.replace(text, "").trim()
                if (stripped != text) {
                    text = stripped
                    changed = true
                }
            }
            val withoutTrailing = trailingFillers.replace(text, "")
            if (withoutTrailing != text) {
                text = withoutTrailing
                changed = true
            }
        }
        return text
    }
}
