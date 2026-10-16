package com.example.pfptodo.data.local.dao

import androidx.room.*
import com.example.pfptodo.data.local.entity.TagEntity
import com.example.pfptodo.data.local.entity.TaskTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTagsRaw(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN task_tag_cross_ref ON tags.id = task_tag_cross_ref.tagId
        WHERE task_tag_cross_ref.taskId = :taskId
        ORDER BY tags.name ASC
        """
    )
    fun getTagsForTask(taskId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN task_tag_cross_ref ON tags.id = task_tag_cross_ref.tagId
        WHERE task_tag_cross_ref.taskId = :taskId
        ORDER BY tags.name ASC
        """
    )
    suspend fun getTagsForTaskSync(taskId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskTagCrossRef(crossRef: TaskTagCrossRef)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun deleteTaskTagCrossRef(taskId: Long, tagId: Long)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun deleteTagsForTask(taskId: Long)

    @Query("SELECT * FROM task_tag_cross_ref")
    suspend fun getAllTaskTagCrossRefsRaw(): List<TaskTagCrossRef>

    @Transaction
    suspend fun setTaskTags(taskId: Long, tagIds: List<Long>) {
        deleteTagsForTask(taskId)
        for (tagId in tagIds) {
            insertTaskTagCrossRef(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }
    }
}
