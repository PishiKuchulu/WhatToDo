package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.first

/**
 * Use case for reordering subtasks under a parent task.
 *
 * Every id in [orderedSubtaskIds] is validated against the parent's actual subtasks
 * before any row is touched: an id that does not belong to [parentId] (stale UI state,
 * a task from another parent, a top-level task) aborts the reorder with an error instead
 * of silently renumbering foreign rows. When a [TransactionRunner] is wired (production,
 * see AppContainer) the order updates additionally commit atomically.
 */
class ReorderSubtasksUseCase(
    private val taskRepository: TaskRepository,
    private val transactionRunner: TransactionRunner? = null
) {
    suspend operator fun invoke(parentId: Long, orderedSubtaskIds: List<Long>): Result<Unit> {
        if (orderedSubtaskIds.size != orderedSubtaskIds.toSet().size) {
            return Result.Error(IllegalArgumentException("Duplicate subtask ids in reorder for parent $parentId"))
        }

        val ownedIds = taskRepository.getSubtasksForTask(parentId).first().map { it.id }.toSet()
        val foreignIds = orderedSubtaskIds.filter { it !in ownedIds }
        if (foreignIds.isNotEmpty()) {
            return Result.Error(
                IllegalArgumentException("Subtask ids $foreignIds do not belong to parent task $parentId")
            )
        }

        return try {
            val runner = transactionRunner
            if (runner != null) {
                runner.inTransaction { applyOrder(orderedSubtaskIds) }
            } else {
                applyOrder(orderedSubtaskIds)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Applies the new order indices, throwing on the first failure so a surrounding
     * transaction rolls back; without one the caller converts the throw to [Result.Error],
     * preserving the previous fail-fast contract.
     */
    private suspend fun applyOrder(orderedSubtaskIds: List<Long>) {
        orderedSubtaskIds.forEachIndexed { index, subtaskId ->
            when (val result = taskRepository.updateTaskOrder(subtaskId, index)) {
                is Result.Success -> Unit
                is Result.Error -> throw result.exception
                is Result.Loading -> throw IllegalStateException("Loading is not a terminal result")
            }
        }
    }
}
