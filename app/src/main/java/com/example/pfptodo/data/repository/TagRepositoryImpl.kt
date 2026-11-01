package com.example.pfptodo.data.repository

import android.util.Log

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
        }.catch { e ->
            Log.e(TAG, "Failed to load tags; falling back to empty list", e)
            emit(emptyList())
        }
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
            // Canonicalize on creation (M-23): name lookups are case-insensitive
            // (TagDao.getTagByName uses LOWER()), but the unique index on `name`
            // is case-sensitive — so inserting a case-variant (or padded) name
            // would create a second row for what every lookup treats as the
            // same tag. Trim, look up case-insensitively first, and reuse the
            // existing row. Every creation path funnels through here: the
            // AddTag use case (settings UI create, task-editor create and its
            // get-or-create fallback, smart-add in the task list) and backup
            // import's direct insert.
            val canonical = tag.copy(name = tag.name.trim())
            val existing = tagDao.getTagByName(canonical.name)
            if (existing != null) {
                Result.Success(existing.id)
            } else {
                val id = tagDao.insertTag(canonical.toEntity())
                Result.Success(id)
            }
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
        }.catch { e ->
            Log.e(TAG, "Failed to load tags for task; falling back to empty list", e)
            emit(emptyList())
        }
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

    private companion object {
        const val TAG = "TagRepositoryImpl"
    }
}
