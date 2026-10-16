package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.SuggestionAction
import com.example.pfptodo.domain.model.SuggestionType
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskSuggestion
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure, deterministic domain use case for generating smart proactive task suggestions.
 * Evaluates active task states against prioritized productivity rules.
 */
class GetSmartSuggestionsUseCase {

    companion object {
        const val MAX_VISIBLE_SUGGESTIONS = 3
        private const val STALE_DAYS_THRESHOLD = 14L
    }

    /**
     * Evaluates task collection and produces a ranked, deduplicated list of up to 3 suggestions.
     *
     * @param tasks Complete list of main tasks.
     * @param dismissedIds Set of suggestion IDs dismissed in the current session.
     * @param clock Time provider for deterministic testing.
     * @return List of at most [MAX_VISIBLE_SUGGESTIONS] ranked [TaskSuggestion]s.
     */
    operator fun invoke(
        tasks: List<Task>,
        dismissedIds: Set<String> = emptySet(),
        clock: Clock = Clock.systemDefaultZone()
    ): List<TaskSuggestion> {
        if (tasks.isEmpty()) return emptyList()

        val activeTasks = tasks.filter { !it.isCompleted && it.parentTaskId == null }
        if (activeTasks.isEmpty()) return emptyList()

        val zone = clock.zone ?: ZoneId.systemDefault()
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()
        val staleThresholdInstant = now.minusDays(STALE_DAYS_THRESHOLD).toInstant()

        val rawSuggestions = mutableListOf<TaskSuggestion>()

        // 1. OVERDUE_HIGH_PRIORITY (Rank 100)
        val overdueHighPriorityTasks = activeTasks.filter {
            it.priority == Priority.HIGH && it.dueDate != null && it.dueDate.isBefore(startOfToday)
        }.sortedBy { it.dueDate }

        for (task in overdueHighPriorityTasks) {
            val id = "overdue_high_${task.id}"
            if (id !in dismissedIds) {
                rawSuggestions.add(
                    TaskSuggestion(
                        id = id,
                        type = SuggestionType.OVERDUE_HIGH_PRIORITY,
                        rank = 100,
                        titleRes = R.string.suggestion_overdue_high_title,
                        descriptionRes = R.string.suggestion_overdue_high_desc,
                        formatArgs = listOf(task.title),
                        relatedTaskId = task.id,
                        action = SuggestionAction.OpenTask(task.id)
                    )
                )
            }
        }

        // 2. OVERDUE_CLEANUP (Rank 90) - Trigger if multiple overdue tasks exist
        val overdueTasks = activeTasks.filter {
            it.dueDate != null && it.dueDate.isBefore(startOfToday)
        }
        if (overdueTasks.size >= 2) {
            val id = "overdue_cleanup_${overdueTasks.size}"
            if (id !in dismissedIds) {
                rawSuggestions.add(
                    TaskSuggestion(
                        id = id,
                        type = SuggestionType.OVERDUE_CLEANUP,
                        rank = 90,
                        titleRes = R.string.suggestion_overdue_cleanup_title,
                        descriptionRes = R.string.suggestion_overdue_cleanup_desc,
                        formatArgs = listOf(overdueTasks.size.toString()),
                        relatedTaskId = null,
                        action = SuggestionAction.FilterOverdue
                    )
                )
            }
        }

        // 3. HIGH_PRIORITY_UNSCHEDULED (Rank 80)
        val unscheduledHighTasks = activeTasks.filter {
            it.priority == Priority.HIGH && it.dueDate == null
        }.sortedByDescending { it.createdAt }

        for (task in unscheduledHighTasks) {
            val id = "unscheduled_high_${task.id}"
            if (id !in dismissedIds) {
                rawSuggestions.add(
                    TaskSuggestion(
                        id = id,
                        type = SuggestionType.HIGH_PRIORITY_UNSCHEDULED,
                        rank = 80,
                        titleRes = R.string.suggestion_unscheduled_high_title,
                        descriptionRes = R.string.suggestion_unscheduled_high_desc,
                        formatArgs = listOf(task.title),
                        relatedTaskId = task.id,
                        action = SuggestionAction.OpenTaskEditor(task.id)
                    )
                )
            }
        }

        // 4. STALE_TASK (Rank 70) - Incomplete task untouched for > 14 days
        val staleTasks = activeTasks.filter {
            it.updatedAt.isBefore(staleThresholdInstant) &&
            it.recurrenceRule is RecurrenceRule.None &&
            (it.dueDate == null || it.dueDate.isBefore(startOfToday))
        }.sortedBy { it.updatedAt }

        for (task in staleTasks) {
            val id = "stale_task_${task.id}"
            if (id !in dismissedIds) {
                rawSuggestions.add(
                    TaskSuggestion(
                        id = id,
                        type = SuggestionType.STALE_TASK,
                        rank = 70,
                        titleRes = R.string.suggestion_stale_title,
                        descriptionRes = R.string.suggestion_stale_desc,
                        formatArgs = listOf(task.title),
                        relatedTaskId = task.id,
                        action = SuggestionAction.OpenTask(task.id)
                    )
                )
            }
        }

        // 5. RECURRING_TASK_REVIEW (Rank 60) - Recurring task that is overdue/missed
        val overdueRecurringTasks = activeTasks.filter {
            it.recurrenceRule !is RecurrenceRule.None &&
            it.dueDate != null && it.dueDate.isBefore(startOfToday)
        }.sortedBy { it.dueDate }

        for (task in overdueRecurringTasks) {
            val id = "recurring_review_${task.id}"
            if (id !in dismissedIds) {
                rawSuggestions.add(
                    TaskSuggestion(
                        id = id,
                        type = SuggestionType.RECURRING_TASK_REVIEW,
                        rank = 60,
                        titleRes = R.string.suggestion_recurring_review_title,
                        descriptionRes = R.string.suggestion_recurring_review_desc,
                        formatArgs = listOf(task.title),
                        relatedTaskId = task.id,
                        action = SuggestionAction.OpenTask(task.id)
                    )
                )
            }
        }

        // 6. CHECKLIST_PROGRESS (Rank 50) - Incomplete parent task with partial subtask progress
        val inProgressChecklistTasks = activeTasks.filter {
            it.subtaskCount > 0 && it.completedSubtaskCount > 0 && it.completedSubtaskCount < it.subtaskCount
        }.sortedByDescending { it.subtaskProgress }

        for (task in inProgressChecklistTasks) {
            val id = "checklist_progress_${task.id}"
            if (id !in dismissedIds) {
                rawSuggestions.add(
                    TaskSuggestion(
                        id = id,
                        type = SuggestionType.CHECKLIST_PROGRESS,
                        rank = 50,
                        titleRes = R.string.suggestion_checklist_title,
                        descriptionRes = R.string.suggestion_checklist_desc,
                        formatArgs = listOf(
                            task.title,
                            task.completedSubtaskCount.toString(),
                            task.subtaskCount.toString()
                        ),
                        relatedTaskId = task.id,
                        action = SuggestionAction.OpenTask(task.id)
                    )
                )
            }
        }

        // 7. DAILY_TRIAGE (Rank 40) - 3 or more active tasks due today
        val todayTasks = activeTasks.filter {
            it.dueDate != null && it.dueDate >= startOfToday && it.dueDate.isBefore(startOfTomorrow)
        }
        if (todayTasks.size >= 3) {
            val id = "daily_triage_${todayTasks.size}"
            if (id !in dismissedIds) {
                rawSuggestions.add(
                    TaskSuggestion(
                        id = id,
                        type = SuggestionType.DAILY_TRIAGE,
                        rank = 40,
                        titleRes = R.string.suggestion_daily_triage_title,
                        descriptionRes = R.string.suggestion_daily_triage_desc,
                        formatArgs = listOf(todayTasks.size.toString()),
                        relatedTaskId = null,
                        action = SuggestionAction.FilterToday
                    )
                )
            }
        }

        // Rank-based deduplication and limit
        return rawSuggestions
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TaskSuggestion> { it.rank }.thenBy { it.id })
            .take(MAX_VISIBLE_SUGGESTIONS)
    }
}
