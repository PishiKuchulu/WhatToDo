package com.example.pfptodo.data.local.dao

import androidx.room.*
import com.example.pfptodo.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE taskId = :taskId")
    fun getAttachmentsForTask(taskId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun getAllAttachmentsRaw(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Delete
    suspend fun deleteAttachment(attachment: AttachmentEntity)
}
