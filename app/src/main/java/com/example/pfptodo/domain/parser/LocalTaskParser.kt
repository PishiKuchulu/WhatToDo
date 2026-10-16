package com.example.pfptodo.domain.parser

import com.example.pfptodo.domain.model.ParsedTask
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.TaskParseResult
import com.example.pfptodo.domain.parser.english.EnglishTaskParser
import com.example.pfptodo.domain.parser.persian.PersianTaskParser
import java.time.Clock

/**
 * High-performance, offline-capable local rule parser combining Persian and English NLP extractors.
 */
class LocalTaskParser : TaskParser {

    override fun parse(input: String, clock: Clock): TaskParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return TaskParseResult(
                parsedTask = ParsedTask(title = "", rawInput = ""),
                confidence = 0.0f
            )
        }

        // 1. Try Persian parsing if Persian characters exist or if Jalali dates are present
        val hasPersianChars = trimmed.any { it in '\u0600'..'\u06FF' }
        val parsedTask = if (hasPersianChars) {
            PersianTaskParser.parse(trimmed, clock) ?: EnglishTaskParser.parse(trimmed, clock)
        } else {
            EnglishTaskParser.parse(trimmed, clock)
        }

        // 2. Compute Confidence Score
        var confidence = 0.5f // Baseline for any non-empty title

        if (parsedTask.date != null) confidence += 0.15f
        if (parsedTask.time != null) confidence += 0.10f
        if (parsedTask.priority != Priority.MEDIUM) confidence += 0.10f
        if (parsedTask.tags.isNotEmpty()) confidence += 0.10f
        if (parsedTask.recurrenceRule != RecurrenceRule.None) confidence += 0.05f

        val finalConfidence = confidence.coerceIn(0.0f, 1.0f)

        return TaskParseResult(
            parsedTask = parsedTask,
            confidence = finalConfidence
        )
    }
}
