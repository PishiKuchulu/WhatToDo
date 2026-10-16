package com.example.pfptodo.domain.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing [Task] entities.
 * Handles all database operations related to tasks, including subtasks and search.
 */
interface TaskRepository {
    /**
     * Returns a [Flow] of all top-level tasks (those without a parent).
     */
    fun getAllMainTasks(): Flow<List<Task>>

    /**
     * Searches for tasks matching the given [query] string.
     */
    fun searchTasks(query: String): Flow<List<Task>>

    /**
     * Retrieves a specific task by its [id].
     * Returns a [Result] containing the task or null if not found.
     */
    suspend fun getTaskById(id: Long): Result<Task?>

    /**
     * Observes a specific task by its [id] reactively.
     */
    fun observeTaskById(id: Long): Flow<Task?>

    /**
     * Returns a [Flow] of all subtasks belonging to the given [parentId].
     */
    fun getSubtasksForTask(parentId: Long): Flow<List<Task>>

    /**
     * Returns all tasks in the repository for backup purposes.
     */
    suspend fun getAllTasksRaw(): List<Task>

    /**
     * Inserts a new [task] into the repository.
     * Returns a [Result] with the new task's ID.
     */
    suspend fun insertTask(task: Task): Result<Long>

    /**
     * Updates an existing [task].
     */
    suspend fun updateTask(task: Task): Result<Unit>

    /**
     * Deletes the specified [task].
     */
    suspend fun deleteTask(task: Task): Result<Unit>

    /**
     * Updates the manual display order index of a task.
     */
    suspend fun updateTaskOrder(id: Long, orderIndex: Int): Result<Unit>

    /**
     * Atomically swaps the order indices of two tasks (drag & drop).
     */
    suspend fun swapTaskOrder(idA: Long, idB: Long): Result<Unit>
}
