package com.example.pfptodo.domain.model

data class TaskFilter(
    val status: Status = Status.ALL,
    val priorities: Set<Priority> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val tagFilterMode: TagFilterMode = TagFilterMode.OR,
    val dateFilter: DateFilter = DateFilter.ALL,
    val isRecurringOnly: Boolean = false,
    /**
     * Calendar used for "this week" / "this month". null keeps the legacy behaviour (match either
     * calendar); the task list sets it from the user's calendar setting.
     */
    val calendarType: com.example.pfptodo.core.util.CalendarType? = null
) {
    enum class Status {
        ALL, ACTIVE, COMPLETED, OVERDUE
    }

    enum class DateFilter {
        ALL, TODAY, TOMORROW, THIS_WEEK, THIS_MONTH, UPCOMING
    }

    enum class TagFilterMode {
        OR, AND
    }
}
