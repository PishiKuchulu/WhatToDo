package com.example.pfptodo.data.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.data.local.dao.CategoryDao
import com.example.pfptodo.data.mapper.toDomain
import com.example.pfptodo.data.mapper.toEntity
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(private val categoryDao: CategoryDao) : CategoryRepository {
    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }.catch { emit(emptyList()) }
    }

    override suspend fun getAllCategoriesRaw(): List<Category> {
        return categoryDao.getAllCategoriesRaw().map { it.toDomain() }
    }

    override suspend fun getCategoryById(id: Long): Result<Category?> {
        return try {
            val category = categoryDao.getCategoryById(id)?.toDomain()
            Result.Success(category)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun insertCategory(category: Category): Result<Long> {
        return try {
            val id = categoryDao.insertCategory(category.toEntity())
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateCategory(category: Category): Result<Unit> {
        return try {
            categoryDao.updateCategory(category.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteCategory(category: Category): Result<Unit> {
        return try {
            categoryDao.deleteCategory(category.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
