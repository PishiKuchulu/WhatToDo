package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first

/**
 * Use case for creating a single subtask (checklist item) under a parent task.
 * Enforces a strict 1-level maximum depth constraint (subtasks cannot have nested subtasks).
 */
class CreateSubtaskUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(parentId: Long, title: String): Result<Long> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            return Result.Error(IllegalArgumentException("Subtask title cannot be empty"))
        }

        val parentResult = taskRepository.getTaskById(parentId)
        if (parentResult is Result.Error) {
            return parentResult
        }

        val parentTask = (parentResult as Result.Success).data
            ?: return Result.Error(IllegalArgumentException("Parent task not found for ID: $parentId"))

        // Enforce 1-level depth limit: a subtask cannot become a parent
        if (parentTask.parentTaskId != null) {
            return Result.Error(IllegalStateException("Cannot add a subtask to an existing subtask. Max depth is 1."))
        }

        // Determine next orderIndex
        val existingSubtasks = taskRepository.getSubtasksForTask(parentId).first()
        val nextOrder = (existingSubtasks.maxOfOrNull { it.orderIndex } ?: -1) + 1

        val subtask = Task(
            title = trimmedTitle,
            parentTaskId = parentId,
            orderIndex = nextOrder,
            priority = parentTask.priority
        )

        return taskRepository.insertTask(subtask)
    }
}
