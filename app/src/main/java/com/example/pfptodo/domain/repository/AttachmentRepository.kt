package com.example.pfptodo.domain.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Attachment
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing [Attachment] entities.
 */
interface AttachmentRepository {
    /**
     * Returns a [Flow] of all attachments associated with a specific [taskId].
     */
    fun getAttachmentsForTask(taskId: Long): Flow<List<Attachment>>

    /**
     * Returns all attachments in the repository for backup purposes.
     */
    suspend fun getAllAttachmentsRaw(): List<Attachment>

    /**
     * Inserts a new [attachment].
     */
    suspend fun insertAttachment(attachment: Attachment): Result<Long>

    /**
     * Deletes the specified [attachment].
     */
    suspend fun deleteAttachment(attachment: Attachment): Result<Unit>
}
