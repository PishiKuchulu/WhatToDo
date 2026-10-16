package com.example.pfptodo.ui.screens.taskeditor

import com.example.pfptodo.domain.model.ParsedTask
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import java.time.Instant

/**
 * Initial values handed to the editor when it is opened from the smart quick-add field
 * or from voice input, so that what the user already typed/said is not thrown away.
 */
data class TaskPrefill(
    val title: String = "",
    val dueDate: Instant? = null,
    val priority: Priority = Priority.MEDIUM,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.None,
    val hasReminder: Boolean = false,
    val tagNames: List<String> = emptyList(),
    val subtaskTitles: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = title.isBlank() && dueDate == null && priority == Priority.MEDIUM &&
            recurrenceRule == RecurrenceRule.None && tagNames.isEmpty() && subtaskTitles.isEmpty()

    companion object {
        fun from(parsed: ParsedTask) = TaskPrefill(
            title = parsed.title,
            dueDate = parsed.dueInstant,
            priority = parsed.priority,
            recurrenceRule = parsed.recurrenceRule,
            hasReminder = parsed.hasExplicitReminder && parsed.dueInstant != null,
            tagNames = parsed.tags,
            subtaskTitles = parsed.subtaskTitles
        )
    }
}
