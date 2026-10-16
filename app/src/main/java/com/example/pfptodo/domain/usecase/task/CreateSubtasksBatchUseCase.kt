package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first

/**
 * Use case for batch-creating multiple subtasks under a parent task.
 * Used for smart recognition multi-line checklists and bulk subtask additions.
 */
class CreateSubtasksBatchUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(parentId: Long, titles: List<String>): Result<List<Long>> {
        val validTitles = titles.map { it.trim() }.filter { it.isNotBlank() }
        if (validTitles.isEmpty()) {
            return Result.Success(emptyList())
        }

        val parentResult = taskRepository.getTaskById(parentId)
        if (parentResult is Result.Error) {
            return parentResult
        }

        val parentTask = (parentResult as Result.Success).data
            ?: return Result.Error(IllegalArgumentException("Parent task not found for ID: $parentId"))

        // Enforce 1-level depth limit
        if (parentTask.parentTaskId != null) {
            return Result.Error(IllegalStateException("Cannot add a subtask to an existing subtask. Max depth is 1."))
        }

        val existingSubtasks = taskRepository.getSubtasksForTask(parentId).first()
        var currentOrder = (existingSubtasks.maxOfOrNull { it.orderIndex } ?: -1) + 1

        val insertedIds = mutableListOf<Long>()
        for (title in validTitles) {
            val subtask = Task(
                title = title,
                parentTaskId = parentId,
                orderIndex = currentOrder++,
                priority = parentTask.priority
            )
            val insertResult = taskRepository.insertTask(subtask)
            if (insertResult is Result.Success) {
                insertedIds.add(insertResult.data)
            } else if (insertResult is Result.Error) {
                return insertResult
            }
        }

        return Result.Success(insertedIds)
    }
}
