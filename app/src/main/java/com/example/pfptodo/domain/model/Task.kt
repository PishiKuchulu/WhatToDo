package com.example.pfptodo.domain.model

import java.time.Instant

/**
 * Core domain model representing a single task in the application.
 * Supports hierarchical nesting (subtasks), categorization, and recurrence.
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val parentTaskId: Long? = null,
    val dueDate: Instant? = null,
    val reminderTime: Instant? = null,
    val orderIndex: Int = 0,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.None,
    val recurrenceAnchor: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val subtaskCount: Int = 0,
    val completedSubtaskCount: Int = 0,
    val tags: List<Tag> = emptyList()
) {
    val subtaskProgress: Float
        get() = if (subtaskCount == 0) 0f else (completedSubtaskCount.toFloat() / subtaskCount.toFloat()).coerceIn(0f, 1f)

    val isAllSubtasksCompleted: Boolean
        get() = subtaskCount > 0 && completedSubtaskCount == subtaskCount
}
