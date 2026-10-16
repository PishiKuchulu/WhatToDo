package com.example.pfptodo.domain.model

data class Attachment(
    val id: Long = 0,
    val taskId: Long,
    val type: AttachmentType,
    val uri: String,
    val fileName: String
)
