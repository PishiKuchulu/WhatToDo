package com.example.pfptodo.domain.model

import java.time.Instant

/**
 * Domain model representing a label/tag that can be assigned to tasks.
 */
data class Tag(
    val id: Long = 0,
    val name: String,
    val color: String? = null,
    val createdAt: Instant = Instant.now()
)
