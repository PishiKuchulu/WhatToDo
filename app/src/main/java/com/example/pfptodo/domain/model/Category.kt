package com.example.pfptodo.domain.model

/**
 * Domain model representing a task category.
 * Used for organizing tasks with specific visual identifiers (color and icon).
 */
data class Category(
    val id: Long = 0,
    val name: String,
    val color: String,
    val iconName: String
)
