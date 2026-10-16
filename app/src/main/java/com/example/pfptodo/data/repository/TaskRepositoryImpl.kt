package com.example.pfptodo.data.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.data.local.dao.TaskDao
import com.example.pfptodo.data.mapper.toDomain
import com.example.pfptodo.data.mapper.toEntity
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(private val taskDao: TaskDao) : TaskRepository {
    override fun getAllMainTasks(): Flow<List<Task>> {
        return taskDao.getAllMainTasks().map { entities ->
            entities.map { it.toDomain() }
        }.catch { emit(emptyList()) }
    }

    override fun searchTasks(query: String): Flow<List<Task>> {
        return taskDao.searchTasks(query).map { entities ->
            entities.map { it.toDomain() }
        }.catch { emit(emptyList()) }
    }

    override suspend fun getTaskById(id: Long): Result<Task?> {
        return try {
            val task = taskDao.getTaskWithCountsAndTagsById(id)?.toDomain()
                ?: taskDao.getTaskById(id)?.toDomain()
            Result.Success(task)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun observeTaskById(id: Long): Flow<Task?> {
        return taskDao.observeTaskWithCountsAndTagsById(id).map { it?.toDomain() }
            .catch { emit(null) }
    }

    override fun getSubtasksForTask(parentId: Long): Flow<List<Task>> {
        return taskDao.getSubtasksForTask(parentId).map { entities ->
            entities.map { it.toDomain() }
        }.catch { emit(emptyList()) }
    }

    override suspend fun getAllTasksRaw(): List<Task> {
        return taskDao.getAllTasksRaw().map { it.toDomain() }
    }

    override suspend fun insertTask(task: Task): Result<Long> {
        return try {
            // New top-level tasks go to the end of the manual order. Without a distinct
            // position every task would share orderIndex 0 and drag & drop could not work.
            // The max-order read + insert are atomic inside the DAO transaction.
            val id = taskDao.insertTaskAtEndOfOrder(task.toEntity())
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateTask(task: Task): Result<Unit> {
        return try {
            taskDao.updateTask(task.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteTask(task: Task): Result<Unit> {
        return try {
            taskDao.deleteTask(task.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateTaskOrder(id: Long, orderIndex: Int): Result<Unit> {
        return try {
            taskDao.updateTaskOrder(id, orderIndex)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun swapTaskOrder(idA: Long, idB: Long): Result<Unit> {
        return try {
            // Single UPDATE with CASE — atomic in SQLite, no collision possible.
            taskDao.swapTaskOrder(idA, idB)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
