package com.example.pfptodo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import java.time.Instant

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("categoryId"),
        Index("parentTaskId"),
        Index("orderIndex"),
        Index("title"),
        Index("dueDate")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val parentTaskId: Long? = null,
    val dueDate: Instant? = null,
    val reminderTime: Instant? = null,
    val orderIndex: Int = 0,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.None,
    val recurrenceAnchor: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
