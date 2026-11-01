package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.Flow

/**
 * Wrapper class containing all task-related use cases.
 */
data class TaskUseCases(
    val getFilteredTasks: GetFilteredTasksUseCase,
    val getTaskById: GetTaskById,
    val observeTaskById: ObserveTaskById,
    val getSubtasks: GetSubtasks,
    val addTask: AddTask,
    val updateTask: UpdateTask,
    val deleteTask: DeleteTask,
    val updateTaskOrder: UpdateTaskOrder,
    val swapTaskOrder: SwapTaskOrder,
    val toggleTaskCompletion: ToggleTaskCompletionUseCase,
    val getEisenhowerMatrix: GetEisenhowerMatrixUseCase,
    val rescheduleTask: RescheduleTaskUseCase,
    val createSubtask: CreateSubtaskUseCase,
    val createSubtasksBatch: CreateSubtasksBatchUseCase,
    val reorderSubtasks: ReorderSubtasksUseCase,
    val getSmartSuggestions: GetSmartSuggestionsUseCase = GetSmartSuggestionsUseCase(),
    val parseNaturalLanguageSearch: ParseNaturalLanguageSearchUseCase = ParseNaturalLanguageSearchUseCase(),
    val getSevenDayPlanning: com.example.pfptodo.domain.usecase.calendar.GetSevenDayPlanningUseCase = com.example.pfptodo.domain.usecase.calendar.GetSevenDayPlanningUseCase(),
    val calculateDailyLoadDensity: com.example.pfptodo.domain.usecase.calendar.CalculateDailyLoadDensityUseCase = com.example.pfptodo.domain.usecase.calendar.CalculateDailyLoadDensityUseCase(),
    val calculateRoutineStreaks: com.example.pfptodo.domain.usecase.calendar.CalculateRoutineStreaksUseCase = com.example.pfptodo.domain.usecase.calendar.CalculateRoutineStreaksUseCase()
)

/**
 * Use case for retrieving a single task by its unique ID.
 */
class GetTaskById(private val repository: TaskRepository) {
    suspend operator fun invoke(id: Long): Result<Task?> = repository.getTaskById(id)
}

/**
 * Use case for observing a single task by its unique ID reactively.
 */
class ObserveTaskById(private val repository: TaskRepository) {
    operator fun invoke(id: Long): Flow<Task?> = repository.observeTaskById(id)
}

/**
 * Use case for retrieving all subtasks for a parent task.
 */
class GetSubtasks(private val repository: TaskRepository) {
    operator fun invoke(parentId: Long): Flow<List<Task>> = repository.getSubtasksForTask(parentId)
}

/**
 * Use case for adding a new task.
 *
 * When the task is a subtask ([Task.parentTaskId] != null), the same 1-level depth rule
 * [CreateSubtaskUseCase] enforces is applied here too, so every creation path (editor,
 * quick add) is guarded: a subtask can never become a parent.
 */
class AddTask(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task): Result<Long> {
        val parentId = task.parentTaskId
        if (parentId != null) {
            val parentResult = repository.getTaskById(parentId)
            if (parentResult is Result.Error) {
                return parentResult
            }

            val parentTask = (parentResult as Result.Success).data
                ?: return Result.Error(IllegalArgumentException("Parent task not found for ID: $parentId"))

            if (parentTask.parentTaskId != null) {
                return Result.Error(IllegalStateException("Cannot add a subtask to an existing subtask. Max depth is 1."))
            }
        }
        return repository.insertTask(task)
    }
}

/**
 * Use case for updating an existing task's data.
 */
class UpdateTask(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task): Result<Unit> = repository.updateTask(task)
}

/**
 * Use case for deleting a task.
 *
 * ReminderManager is optional so unit tests that only exercise the repository contract keep
 * compiling; production wiring passes the real manager (see AppContainer).
 */
class DeleteTask(
    private val repository: TaskRepository,
    private val reminderManager: ReminderManager? = null
) {
    suspend operator fun invoke(task: Task): Result<Unit> {
        val reminders = reminderManager
        if (reminders != null) {
            try {
                // Subtasks vanish silently via FK CASCADE, so cancel their reminders
                // before the parent row is gone. The walk is recursive: the app
                // enforces max depth 1 today, but legacy or imported trees can be
                // deeper, and a surviving grandchild reminder would fire for a
                // task that no longer exists. Also cancel the parent's own
                // reminder — not every delete flow (e.g. calendar, matrix) does
                // this itself.
                cancelRemindersRecursively(task.id, reminders)
                reminders.cancelReminder(task.id)
            } catch (_: Exception) {
                // Reminder cancellation is best-effort; the delete must still proceed.
            }
        }
        return repository.deleteTask(task)
    }

    private suspend fun cancelRemindersRecursively(parentId: Long, reminders: ReminderManager) {
        repository.getSubtasksForTask(parentId).firstOrNull()?.forEach { subtask ->
            cancelRemindersRecursively(subtask.id, reminders)
            reminders.cancelReminder(subtask.id)
        }
    }
}

/**
 * Use case for updating the display order index of a task.
 */
class UpdateTaskOrder(private val repository: TaskRepository) {
    suspend operator fun invoke(id: Long, orderIndex: Int): Result<Unit> = repository.updateTaskOrder(id, orderIndex)
}

/**
 * Use case for atomically swapping the order indices of two tasks (drag & drop).
 */
class SwapTaskOrder(private val repository: TaskRepository) {
    suspend operator fun invoke(idA: Long, idB: Long): Result<Unit> = repository.swapTaskOrder(idA, idB)
}
