package com.example.pfptodo.data.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.data.local.dao.AttachmentDao
import com.example.pfptodo.data.local.entity.AttachmentEntity
import com.example.pfptodo.domain.model.Attachment
import com.example.pfptodo.domain.repository.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AttachmentRepositoryImpl(private val attachmentDao: AttachmentDao) : AttachmentRepository {
    override fun getAttachmentsForTask(taskId: Long): Flow<List<Attachment>> {
        return attachmentDao.getAttachmentsForTask(taskId).map { entities ->
            entities.map { it.toDomain() }
        }.catch { emit(emptyList()) }
    }

    override suspend fun getAllAttachmentsRaw(): List<Attachment> {
        return attachmentDao.getAllAttachmentsRaw().map { it.toDomain() }
    }

    override suspend fun insertAttachment(attachment: Attachment): Result<Long> {
        return try {
            val id = attachmentDao.insertAttachment(attachment.toEntity())
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteAttachment(attachment: Attachment): Result<Unit> {
        return try {
            attachmentDao.deleteAttachment(attachment.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

fun AttachmentEntity.toDomain(): Attachment = Attachment(id, taskId, type, uri, fileName)
fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(id, taskId, type, uri, fileName)
