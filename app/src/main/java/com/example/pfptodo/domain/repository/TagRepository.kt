package com.example.pfptodo.domain.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing [Tag] entities and their many-to-many
 * associations with tasks.
 */
interface TagRepository {
    /**
     * Returns a [Flow] of all available tags ordered by name.
     */
    fun getAllTags(): Flow<List<Tag>>

    /**
     * Returns all tags in the repository for backup/export.
     */
    suspend fun getAllTagsRaw(): List<Tag>

    /**
     * Retrieves a tag by its [id].
     */
    suspend fun getTagById(id: Long): Result<Tag?>

    /**
     * Retrieves a tag by its unique [name].
     */
    suspend fun getTagByName(name: String): Result<Tag?>

    /**
     * Inserts a new [tag].
     */
    suspend fun insertTag(tag: Tag): Result<Long>

    /**
     * Updates an existing [tag].
     */
    suspend fun updateTag(tag: Tag): Result<Unit>

    /**
     * Deletes the specified [tag].
     */
    suspend fun deleteTag(tag: Tag): Result<Unit>

    /**
     * Returns a [Flow] of all tags associated with a specific [taskId].
     */
    fun getTagsForTask(taskId: Long): Flow<List<Tag>>

    /**
     * Sets the complete list of tags assigned to a task.
     */
    suspend fun setTaskTags(taskId: Long, tagIds: List<Long>): Result<Unit>

    /**
     * Returns all raw task-tag cross references for backup/export.
     */
    suspend fun getAllTaskTagCrossRefsRaw(): List<Pair<Long, Long>>

    /**
     * Inserts a task-tag association (primarily for backup import).
     */
    suspend fun insertTaskTagCrossRef(taskId: Long, tagId: Long): Result<Unit>
}
