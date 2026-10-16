package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.repository.TaskRepository

/**
 * Use case for reordering subtasks under a parent task.
 */
class ReorderSubtasksUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(parentId: Long, orderedSubtaskIds: List<Long>): Result<Unit> {
        orderedSubtaskIds.forEachIndexed { index, subtaskId ->
            val result = taskRepository.updateTaskOrder(subtaskId, index)
            if (result is Result.Error) {
                return result
            }
        }
        return Result.Success(Unit)
    }
}
