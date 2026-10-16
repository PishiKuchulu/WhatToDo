package com.example.pfptodo.domain.usecase.task

import android.util.Log
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TagRepository
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.repository.TransactionRunner
import com.example.pfptodo.domain.util.RecurrenceEngine
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant

/**
 * Encapsulates the complete business logic for toggling a task's completion state.
 *
 * Responsibilities:
 * 1. Toggles [Task.isCompleted] and timestamps [Task.completedAt].
 * 2. Synchronizes [ReminderManager]: cancels pending reminders on completion,
 *    and reschedules them if an uncompleted task has an active reminderTime.
 * 3. Spawns the next recurring occurrence via [RecurrenceEngine] when a recurring task
 *    is completed, preserving the series anchor, subtasks (checklist), tags, and reminder preferences.
 * 4. Persists changes to [TaskRepository].
 */
class ToggleTaskCompletionUseCase(
    private val repository: TaskRepository,
    private val reminderManager: ReminderManager,
    private val tagRepository: TagRepository? = null,
    private val transactionRunner: TransactionRunner? = null
) {
    suspend operator fun invoke(task: Task): Result<Unit> {
        val now = Instant.now()
        val wasCompleted = task.isCompleted
        val updatedTask = task.copy(
            isCompleted = !task.isCompleted,
            completedAt = if (!task.isCompleted) now else null,
            updatedAt = now
        )

        val updateResult = repository.updateTask(updatedTask)
        if (updateResult is Result.Error) {
            return updateResult
        }

        if (!wasCompleted && updatedTask.isCompleted) {
            // Task was completed: cancel any pending reminder
            reminderManager.cancelReminder(task.id)
        } else if (wasCompleted && !updatedTask.isCompleted) {
            // Task was uncompleted: reschedule reminder if it has one
            if (updatedTask.reminderTime != null) {
                reminderManager.scheduleReminder(updatedTask)
            }
        }

        if (updatedTask.recurrenceRule !is RecurrenceRule.None) {
            if (updatedTask.isCompleted) {
                val spawnResult = generateNextOccurrence(updatedTask)
                if (spawnResult is Result.Error) {
                    return spawnResult
                }
            } else if (wasCompleted) {
                // Undo / un-complete: the occurrence spawned by the earlier completion is
                // now redundant — remove it so completing again does not create a duplicate.
                removeSpawnedOccurrence(updatedTask)
            }
        }

        return Result.Success(Unit)
    }

    private fun nextDueDateFor(task: Task) = RecurrenceEngine.calculateNextOccurrence(
        anchor = task.recurrenceAnchor ?: task.dueDate ?: task.createdAt,
        from = task.dueDate ?: task.createdAt,
        rule = task.recurrenceRule
    )

    /**
     * Finds the still-open next occurrence of [task]'s series, if one was already spawned
     * (same title, rule, anchor and the computed next due date).
     */
    private suspend fun findSpawnedOccurrence(task: Task): Task? {
        val nextDueDate = nextDueDateFor(task) ?: return null
        return try {
            repository.getAllTasksRaw().firstOrNull { candidate ->
                candidate.id != task.id &&
                    candidate.parentTaskId == null &&
                    !candidate.isCompleted &&
                    candidate.title == task.title &&
                    candidate.recurrenceRule == task.recurrenceRule &&
                    candidate.recurrenceAnchor == task.recurrenceAnchor &&
                    candidate.dueDate == nextDueDate
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun removeSpawnedOccurrence(task: Task) {
        val spawned = findSpawnedOccurrence(task) ?: return
        repository.deleteTask(spawned)
        reminderManager.cancelReminder(spawned.id)
    }

    private suspend fun generateNextOccurrence(task: Task): Result<Unit> {
        val nextDueDate = nextDueDateFor(task)
        // Completing a series task twice (complete -> undo -> complete) must not stack copies.
        if (nextDueDate == null || findSpawnedOccurrence(task) != null) {
            return Result.Success(Unit)
        }

        // Preserve the user's reminder offset (e.g. "2h before due"): only the
        // existence of the reminder was copied before, silently resetting it to
        // the due moment on every spawned occurrence.
        val nextReminderTime = when {
            task.reminderTime != null && task.dueDate != null ->
                nextDueDate.plusMillis(
                    task.reminderTime.toEpochMilli() - task.dueDate.toEpochMilli()
                )
            task.reminderTime != null -> nextDueDate
            else -> null
        }

        val nextTask = task.copy(
            id = 0,
            isCompleted = false,
            completedAt = null,
            dueDate = nextDueDate,
            reminderTime = nextReminderTime,
            recurrenceAnchor = task.recurrenceAnchor,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        return try {
            // All DB writes for the spawned occurrence (task + subtasks + tags) run in
            // one transaction: a failure anywhere rolls everything back instead of
            // leaving a half-copied occurrence behind.
            suspend fun spawnInDb(): Long {
                val newTaskId = repository.insertTask(nextTask).unwrap()

                // 1. Copy subtasks (checklist items) to the new occurrence
                val existingSubtasks = repository.getSubtasksForTask(task.id).firstOrNull() ?: emptyList()
                for (subtask in existingSubtasks) {
                    repository.insertTask(
                        subtask.copy(
                            id = 0,
                            parentTaskId = newTaskId,
                            isCompleted = false,
                            completedAt = null,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    ).unwrap()
                }

                // 2. Preserve tags for the new occurrence
                if (task.tags.isNotEmpty() && tagRepository != null) {
                    tagRepository.setTaskTags(newTaskId, task.tags.map { it.id }).unwrap()
                }

                return newTaskId
            }

            val newTaskId = if (transactionRunner != null) {
                transactionRunner.inTransaction { spawnInDb() }
            } else {
                spawnInDb()
            }

            // 3. Schedule the reminder only after the DB writes committed — it is a
            // side effect outside the transaction, so a rollback never leaves an
            // orphaned reminder for a task that was not persisted.
            if (nextTask.reminderTime != null) {
                reminderManager.scheduleReminder(nextTask.copy(id = newTaskId))
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to spawn next occurrence of recurring task ${task.id}", e)
            Result.Error(e)
        }
    }

    private fun <T> Result<T>.unwrap(): T = when (this) {
        is Result.Success -> data
        is Result.Error -> throw exception
        is Result.Loading -> throw IllegalStateException("Loading is not a terminal result")
    }

    companion object {
        private const val TAG = "ToggleTaskCompletion"
    }
}
