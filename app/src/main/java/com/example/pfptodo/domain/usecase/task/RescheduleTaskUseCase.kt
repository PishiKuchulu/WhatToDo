package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.worker.reminder.ReminderManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Use case to atomically snooze or reschedule a task's due date and synchronize its reminder.
 */
class RescheduleTaskUseCase(
    private val repository: TaskRepository,
    private val reminderManager: ReminderManager
) {
    suspend fun snoozeDays(task: Task, days: Long = 1): Result<Unit> {
        val zone = ZoneId.systemDefault()
        val currentDueDate = task.dueDate?.atZone(zone)
        val now = LocalDate.now(zone)
        
        val newZonedDateTime = if (currentDueDate != null) {
            val baseDate = if (currentDueDate.toLocalDate().isBefore(now)) now else currentDueDate.toLocalDate()
            baseDate.plusDays(days).atTime(currentDueDate.toLocalTime()).atZone(zone)
        } else {
            now.plusDays(days).atTime(9, 0).atZone(zone)
        }
        val newInstant = newZonedDateTime.toInstant()

        val updatedTask = task.copy(
            dueDate = newInstant,
            reminderTime = if (task.reminderTime != null) newInstant else null,
            updatedAt = Instant.now()
        )

        val result = repository.updateTask(updatedTask)
        if (result is Result.Success && updatedTask.reminderTime != null && !updatedTask.isCompleted) {
            reminderManager.scheduleReminder(updatedTask)
        }
        return result
    }

    suspend fun setDueDate(task: Task, newDueDate: Instant?): Result<Unit> {
        val updatedTask = task.copy(
            dueDate = newDueDate,
            reminderTime = if (newDueDate == null) null else task.reminderTime,
            updatedAt = Instant.now()
        )
        val result = repository.updateTask(updatedTask)
        if (result is Result.Success) {
            if (updatedTask.reminderTime != null && !updatedTask.isCompleted) {
                reminderManager.scheduleReminder(updatedTask)
            } else if (newDueDate == null) {
                reminderManager.cancelReminder(task.id)
            }
        }
        return result
    }
}
