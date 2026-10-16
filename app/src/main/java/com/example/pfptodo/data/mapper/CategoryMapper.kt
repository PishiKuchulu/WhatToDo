package com.example.pfptodo.data.mapper

import com.example.pfptodo.data.local.entity.CategoryEntity
import com.example.pfptodo.domain.model.Category

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        color = color,
        iconName = iconName
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        color = color,
        iconName = iconName
    )
}
