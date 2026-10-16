package com.example.pfptodo.domain.model

/**
 * Result of parsing a natural language string.
 *
 * @property parsedTask The extracted structured task data.
 * @property confidence Confidence score between 0.0 (low/raw title only) and 1.0 (high/exact matches).
 * @property extractedTokens List of specific tokens extracted from the raw input.
 */
data class TaskParseResult(
    val parsedTask: ParsedTask,
    val confidence: Float,
    val extractedTokens: List<ExtractedToken> = emptyList()
)

data class ExtractedToken(
    val type: TokenType,
    val rawText: String,
    val range: IntRange
)

enum class TokenType {
    TAG,
    DATE,
    TIME,
    PRIORITY,
    RECURRENCE,
    REMINDER
}
