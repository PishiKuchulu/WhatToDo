package com.example.pfptodo.data.mapper

import com.example.pfptodo.data.local.dao.TaskWithCounts
import com.example.pfptodo.data.local.entity.TaskEntity
import com.example.pfptodo.domain.model.Task

import com.example.pfptodo.domain.model.Tag

fun TaskEntity.toDomain(
    subtaskCount: Int = 0,
    completedSubtaskCount: Int = 0,
    tags: List<Tag> = emptyList()
): Task {
    return Task(
        id = id,
        title = title,
        description = description,
        notes = notes,
        isCompleted = isCompleted,
        completedAt = completedAt,
        priority = priority,
        categoryId = categoryId,
        parentTaskId = parentTaskId,
        dueDate = dueDate,
        reminderTime = reminderTime,
        orderIndex = orderIndex,
        recurrenceRule = recurrenceRule,
        recurrenceAnchor = recurrenceAnchor,
        createdAt = createdAt,
        updatedAt = updatedAt,
        subtaskCount = subtaskCount,
        completedSubtaskCount = completedSubtaskCount,
        tags = tags
    )
}

fun TaskWithCounts.toDomain(): Task {
    return task.toDomain(
        subtaskCount = subtaskCount,
        completedSubtaskCount = completedSubtaskCount,
        tags = tags.map { it.toDomain() }
    )
}

fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        title = title,
        description = description,
        notes = notes,
        isCompleted = isCompleted,
        completedAt = completedAt,
        priority = priority,
        categoryId = categoryId,
        parentTaskId = parentTaskId,
        dueDate = dueDate,
        reminderTime = reminderTime,
        orderIndex = orderIndex,
        recurrenceRule = recurrenceRule,
        recurrenceAnchor = recurrenceAnchor,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
