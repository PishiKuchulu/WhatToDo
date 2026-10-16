package com.example.pfptodo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.example.pfptodo.data.local.entity.TagEntity
import com.example.pfptodo.data.local.entity.TaskEntity
import com.example.pfptodo.data.local.entity.TaskTagCrossRef

@Dao
interface TaskDao {
    @Transaction
    @Query(
        """
        SELECT *,
        (SELECT COUNT(*) FROM tasks AS t2 WHERE t2.parentTaskId = tasks.id) AS subtaskCount,
        (SELECT COUNT(*) FROM tasks AS t3 WHERE t3.parentTaskId = tasks.id AND t3.isCompleted = 1) AS completedSubtaskCount
        FROM tasks
        WHERE parentTaskId IS NULL
        ORDER BY orderIndex ASC
        """
    )
    fun getAllMainTasks(): Flow<List<TaskWithCountsAndTags>>

    @Transaction
    @Query(
        """
        SELECT *,
        (SELECT COUNT(*) FROM tasks AS t2 WHERE t2.parentTaskId = tasks.id) AS subtaskCount,
        (SELECT COUNT(*) FROM tasks AS t3 WHERE t3.parentTaskId = tasks.id AND t3.isCompleted = 1) AS completedSubtaskCount
        FROM tasks
        WHERE parentTaskId IS NULL
        AND (
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(title, 'ي', 'ی'), 'ك', 'ک'), 'أ', 'ا'), 'إ', 'ا'), 'ة', 'ه') LIKE '%' || :query || '%' ESCAPE '\'
            OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(description, 'ي', 'ی'), 'ك', 'ک'), 'أ', 'ا'), 'إ', 'ا'), 'ة', 'ه') LIKE '%' || :query || '%' ESCAPE '\'
            OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(notes, 'ي', 'ی'), 'ك', 'ک'), 'أ', 'ا'), 'إ', 'ا'), 'ة', 'ه') LIKE '%' || :query || '%' ESCAPE '\'
            OR EXISTS (
                SELECT 1 FROM task_tag_cross_ref
                INNER JOIN tags ON task_tag_cross_ref.tagId = tags.id
                WHERE task_tag_cross_ref.taskId = tasks.id
                AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(tags.name, 'ي', 'ی'), 'ك', 'ک'), 'أ', 'ا'), 'إ', 'ا'), 'ة', 'ه') LIKE '%' || :query || '%' ESCAPE '\'
            )
        )
        """
    )
    fun searchTasks(query: String): Flow<List<TaskWithCountsAndTags>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Transaction
    @Query(
        """
        SELECT *,
        (SELECT COUNT(*) FROM tasks AS t2 WHERE t2.parentTaskId = tasks.id) AS subtaskCount,
        (SELECT COUNT(*) FROM tasks AS t3 WHERE t3.parentTaskId = tasks.id AND t3.isCompleted = 1) AS completedSubtaskCount
        FROM tasks
        WHERE id = :id
        """
    )
    suspend fun getTaskWithCountsAndTagsById(id: Long): TaskWithCountsAndTags?

    @Transaction
    @Query(
        """
        SELECT *,
        (SELECT COUNT(*) FROM tasks AS t2 WHERE t2.parentTaskId = tasks.id) AS subtaskCount,
        (SELECT COUNT(*) FROM tasks AS t3 WHERE t3.parentTaskId = tasks.id AND t3.isCompleted = 1) AS completedSubtaskCount
        FROM tasks
        WHERE id = :id
        """
    )
    fun observeTaskWithCountsAndTagsById(id: Long): Flow<TaskWithCountsAndTags?>

    @Transaction
    @Query(
        """
        SELECT *,
        (SELECT COUNT(*) FROM tasks AS t2 WHERE t2.parentTaskId = tasks.id) AS subtaskCount,
        (SELECT COUNT(*) FROM tasks AS t3 WHERE t3.parentTaskId = tasks.id AND t3.isCompleted = 1) AS completedSubtaskCount
        FROM tasks
        WHERE parentTaskId = :parentId
        ORDER BY orderIndex ASC
        """
    )
    fun getSubtasksForTask(parentId: Long): Flow<List<TaskWithCountsAndTags>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksRaw(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    /**
     * Atomically assigns the next manual order position to a new top-level task and
     * inserts it. The read (max orderIndex) + write (insert) run in one transaction,
     * so two concurrent inserts can never compute the same orderIndex and leave two
     * tasks sharing position 0.
     */
    @Transaction
    suspend fun insertTaskAtEndOfOrder(task: TaskEntity): Long {
        val ordered = if (task.parentTaskId == null && task.orderIndex == 0) {
            task.copy(orderIndex = getMaxMainOrderIndex() + 1)
        } else {
            task
        }
        return insertTask(ordered)
    }

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateTaskOrder(id: Long, orderIndex: Int)

    /**
     * Transactional swap of two tasks' orderIndex values (drag & drop).
     *
     * Older builds stored every new task with orderIndex = 0, which made a plain swap a
     * no-op (0 <-> 0). When the two values are equal the main tasks are first renumbered
     * to distinct, gap-free positions (in their current display order) and then swapped.
     */
    @androidx.room.Transaction
    suspend fun swapTaskOrder(idA: Long, idB: Long) {
        var orderA = getOrderIndex(idA)
        var orderB = getOrderIndex(idB)
        if (orderA == orderB) {
            getMainTaskIdsInOrder().forEachIndexed { position, id -> updateTaskOrder(id, position) }
            orderA = getOrderIndex(idA)
            orderB = getOrderIndex(idB)
        }
        updateTaskOrder(idA, orderB)
        updateTaskOrder(idB, orderA)
    }

    /** Highest manual position among top-level tasks, or -1 when there are none. */
    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM tasks WHERE parentTaskId IS NULL")
    suspend fun getMaxMainOrderIndex(): Int

    @Query("SELECT id FROM tasks WHERE parentTaskId IS NULL ORDER BY orderIndex ASC, id ASC")
    suspend fun getMainTaskIdsInOrder(): List<Long>

    @Query("SELECT orderIndex FROM tasks WHERE id = :id")
    suspend fun getOrderIndex(id: Long): Int
}

data class TaskWithCountsAndTags(
    @androidx.room.Embedded val task: TaskEntity,
    val subtaskCount: Int,
    val completedSubtaskCount: Int,
    @androidx.room.Relation(
        parentColumn = "id",
        entity = TagEntity::class,
        entityColumn = "id",
        associateBy = androidx.room.Junction(
            value = TaskTagCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity> = emptyList()
)

typealias TaskWithCounts = TaskWithCountsAndTags