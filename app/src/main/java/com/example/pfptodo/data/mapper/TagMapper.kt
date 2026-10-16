package com.example.pfptodo.data.mapper

import com.example.pfptodo.data.local.entity.TagEntity
import com.example.pfptodo.domain.model.Tag

fun TagEntity.toDomain(): Tag {
    return Tag(
        id = id,
        name = name,
        color = color,
        createdAt = createdAt
    )
}

fun Tag.toEntity(): TagEntity {
    return TagEntity(
        id = id,
        name = name,
        color = color,
        createdAt = createdAt
    )
}
