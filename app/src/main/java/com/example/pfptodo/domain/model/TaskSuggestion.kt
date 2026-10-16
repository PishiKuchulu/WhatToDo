package com.example.pfptodo.domain.model

/**
 * Domain model representing a proactive, deterministic smart suggestion.
 * Generated purely in-memory from current task states without persistent database overhead.
 */
data class TaskSuggestion(
    val id: String,
    val type: SuggestionType,
    val rank: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val formatArgs: List<String> = emptyList(),
    val relatedTaskId: Long? = null,
    val action: SuggestionAction
)

enum class SuggestionType {
    OVERDUE_HIGH_PRIORITY,
    OVERDUE_CLEANUP,
    HIGH_PRIORITY_UNSCHEDULED,
    STALE_TASK,
    RECURRING_TASK_REVIEW,
    CHECKLIST_PROGRESS,
    DAILY_TRIAGE
}

sealed class SuggestionAction {
    object FilterOverdue : SuggestionAction()
    object FilterToday : SuggestionAction()
    data class OpenTask(val taskId: Long) : SuggestionAction()
    data class OpenTaskEditor(val taskId: Long) : SuggestionAction()
}
