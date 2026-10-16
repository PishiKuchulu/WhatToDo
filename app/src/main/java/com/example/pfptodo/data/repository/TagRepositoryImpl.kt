package com.example.pfptodo.data.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.data.local.dao.TagDao
import com.example.pfptodo.data.local.entity.TaskTagCrossRef
import com.example.pfptodo.data.mapper.toDomain
import com.example.pfptodo.data.mapper.toEntity
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class TagRepositoryImpl(private val tagDao: TagDao) : TagRepository {
    override fun getAllTags(): Flow<List<Tag>> {
        return tagDao.getAllTags().map { entities ->
            entities.map { it.toDomain() }
        }.catch { emit(emptyList()) }
    }

    override suspend fun getAllTagsRaw(): List<Tag> {
        return tagDao.getAllTagsRaw().map { it.toDomain() }
    }

    override suspend fun getTagById(id: Long): Result<Tag?> {
        return try {
            val tag = tagDao.getTagById(id)?.toDomain()
            Result.Success(tag)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getTagByName(name: String): Result<Tag?> {
        return try {
            val tag = tagDao.getTagByName(name)?.toDomain()
            Result.Success(tag)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun insertTag(tag: Tag): Result<Long> {
        return try {
            val id = tagDao.insertTag(tag.toEntity())
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateTag(tag: Tag): Result<Unit> {
        return try {
            tagDao.updateTag(tag.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteTag(tag: Tag): Result<Unit> {
        return try {
            tagDao.deleteTag(tag.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun getTagsForTask(taskId: Long): Flow<List<Tag>> {
        return tagDao.getTagsForTask(taskId).map { entities ->
            entities.map { it.toDomain() }
        }.catch { emit(emptyList()) }
    }

    override suspend fun setTaskTags(taskId: Long, tagIds: List<Long>): Result<Unit> {
        return try {
            tagDao.setTaskTags(taskId, tagIds)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getAllTaskTagCrossRefsRaw(): List<Pair<Long, Long>> {
        return tagDao.getAllTaskTagCrossRefsRaw().map { Pair(it.taskId, it.tagId) }
    }

    override suspend fun insertTaskTagCrossRef(taskId: Long, tagId: Long): Result<Unit> {
        return try {
            tagDao.insertTaskTagCrossRef(TaskTagCrossRef(taskId = taskId, tagId = tagId))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
