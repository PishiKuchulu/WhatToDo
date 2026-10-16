package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.EisenhowerMatrix
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Use case that partitions active main tasks into the 4 Eisenhower Matrix quadrants.
 *
 * - Quadrant 1 (Do First): Urgent & Important (Priority HIGH && (Overdue || Due within 2 days))
 * - Quadrant 2 (Schedule): Not Urgent & Important (Priority HIGH && !(Overdue || Due within 2 days))
 * - Quadrant 3 (Delegate): Urgent & Not Important (Priority != HIGH && (Overdue || Due within 2 days))
 * - Quadrant 4 (Eliminate / Backlog): Not Urgent & Not Important (Priority != HIGH && !(Overdue || Due within 2 days))
 */
class GetEisenhowerMatrixUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<EisenhowerMatrix> {
        return repository.getAllMainTasks().map { tasks ->
            val activeTasks = tasks.filter { !it.isCompleted }
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val startOfDayAfterTomorrow = now.toLocalDate().plusDays(2).atStartOfDay(zone).toInstant()

            val q1 = mutableListOf<Task>()
            val q2 = mutableListOf<Task>()
            val q3 = mutableListOf<Task>()
            val q4 = mutableListOf<Task>()

            for (task in activeTasks) {
                val isUrgent = task.dueDate != null && task.dueDate.isBefore(startOfDayAfterTomorrow)
                val isImportant = task.priority == Priority.HIGH

                when {
                    isUrgent && isImportant -> q1.add(task)
                    !isUrgent && isImportant -> q2.add(task)
                    isUrgent && !isImportant -> q3.add(task)
                    else -> q4.add(task)
                }
            }

            EisenhowerMatrix(
                q1DoFirst = q1,
                q2Schedule = q2,
                q3Delegate = q3,
                q4Eliminate = q4
            )
        }
    }
}
