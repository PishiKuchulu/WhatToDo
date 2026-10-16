package com.example.pfptodo.domain.usecase.calendar

import com.example.pfptodo.domain.model.DayLoadDensity
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calculates deterministic workload density for a given date based on scheduled tasks,
 * overdue tasks, and high-priority items.
 *
 * Scoring Rule:
 * loadScore = scheduledTaskCount + (highPriorityUncompletedCount * 1) + (overdueCountIfToday * 1)
 *
 * - NONE: loadScore == 0
 * - LOW: loadScore in 1..2
 * - MEDIUM: loadScore in 3..4
 * - HIGH: loadScore >= 5
 */
class CalculateDailyLoadDensityUseCase(
    private val clock: Clock = Clock.systemDefaultZone()
) {

    operator fun invoke(date: LocalDate, allTasks: List<Task>): DayLoadDensity {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val startOfToday = today.atStartOfDay(zone).toInstant()

        val scheduledTasksForDate = allTasks.filter { task ->
            task.dueDate?.let { it.atZone(zone).toLocalDate() == date } ?: false
        }

        val scheduledCount = scheduledTasksForDate.size
        val highPriorityCount = scheduledTasksForDate.count { it.priority == Priority.HIGH && !it.isCompleted }

        val overdueCount = if (date == today) {
            allTasks.count { task ->
                !task.isCompleted && task.dueDate != null && task.dueDate.isBefore(startOfToday)
            }
        } else {
            0
        }

        val compositeScore = scheduledCount + highPriorityCount + overdueCount

        return when {
            compositeScore == 0 -> DayLoadDensity.NONE
            compositeScore in 1..2 -> DayLoadDensity.LOW
            compositeScore in 3..4 -> DayLoadDensity.MEDIUM
            else -> DayLoadDensity.HIGH
        }
    }
}
