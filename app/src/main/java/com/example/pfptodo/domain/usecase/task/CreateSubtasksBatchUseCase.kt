package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.first

/**
 * Use case for batch-creating multiple subtasks under a parent task.
 * Used for smart recognition multi-line checklists and bulk subtask additions.
 *
 * When a [TransactionRunner] is wired (production, see AppContainer) the inserts run in a
 * single transaction: a failure part-way rolls the whole batch back instead of leaving a
 * partial checklist behind. Without one the inserts run sequentially and the first failure
 * aborts the batch (rows already inserted stay — the legacy behaviour tests rely on).
 */
class CreateSubtasksBatchUseCase(
    private val taskRepository: TaskRepository,
    private val transactionRunner: TransactionRunner? = null
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
        val startOrder = (existingSubtasks.maxOfOrNull { it.orderIndex } ?: -1) + 1

        return try {
            val runner = transactionRunner
            val insertedIds = if (runner != null) {
                runner.inTransaction { insertAll(parentId, parentTask, validTitles, startOrder) }
            } else {
                insertAll(parentId, parentTask, validTitles, startOrder)
            }
            Result.Success(insertedIds)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Inserts every subtask, throwing on the first failure so a surrounding transaction
     * rolls back; without a transaction the caller converts the throw to [Result.Error],
     * preserving the previous fail-fast contract.
     */
    private suspend fun insertAll(
        parentId: Long,
        parentTask: Task,
        validTitles: List<String>,
        startOrder: Int
    ): List<Long> {
        var currentOrder = startOrder
        val insertedIds = mutableListOf<Long>()
        for (title in validTitles) {
            val subtask = Task(
                title = title,
                parentTaskId = parentId,
                orderIndex = currentOrder++,
                priority = parentTask.priority
            )
            when (val insertResult = taskRepository.insertTask(subtask)) {
                is Result.Success -> insertedIds.add(insertResult.data)
                is Result.Error -> throw insertResult.exception
                is Result.Loading -> throw IllegalStateException("Loading is not a terminal result")
            }
        }
        return insertedIds
    }
}
