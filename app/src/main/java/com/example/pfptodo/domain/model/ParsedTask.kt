package com.example.pfptodo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents structured task metadata extracted from natural language input.
 */
data class ParsedTask(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val dueInstant: Instant? = null,
    val priority: Priority = Priority.MEDIUM,
    val tags: List<String> = emptyList(),
    val recurrenceRule: RecurrenceRule = RecurrenceRule.None,
    val hasExplicitReminder: Boolean = false,
    val subtaskTitles: List<String> = emptyList(),
    val rawInput: String = ""
)
