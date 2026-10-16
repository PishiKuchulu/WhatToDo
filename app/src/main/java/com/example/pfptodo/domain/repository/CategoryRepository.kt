package com.example.pfptodo.domain.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing [Category] entities.
 */
interface CategoryRepository {
    /**
     * Returns a [Flow] of all available categories.
     */
    fun getAllCategories(): Flow<List<Category>>

    /**
     * Returns all categories in the repository for backup purposes.
     */
    suspend fun getAllCategoriesRaw(): List<Category>

    /**
     * Retrieves a specific category by its [id].
     */
    suspend fun getCategoryById(id: Long): Result<Category?>

    /**
     * Inserts a new [category].
     */
    suspend fun insertCategory(category: Category): Result<Long>

    /**
     * Updates an existing [category].
     */
    suspend fun updateCategory(category: Category): Result<Unit>

    /**
     * Deletes the specified [category].
     */
    suspend fun deleteCategory(category: Category): Result<Unit>
}
