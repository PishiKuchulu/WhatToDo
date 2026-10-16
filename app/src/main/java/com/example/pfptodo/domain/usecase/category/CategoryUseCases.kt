package com.example.pfptodo.domain.usecase.category

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

data class CategoryUseCases(
    val getAllCategories: GetAllCategories,
    val getCategoryById: GetCategoryById,
    val addCategory: AddCategory,
    val updateCategory: UpdateCategory,
    val deleteCategory: DeleteCategory
)

class GetAllCategories(private val repository: CategoryRepository) {
    operator fun invoke(): Flow<List<Category>> = repository.getAllCategories()
}

class GetCategoryById(private val repository: CategoryRepository) {
    suspend operator fun invoke(id: Long): Result<Category?> = repository.getCategoryById(id)
}

class AddCategory(private val repository: CategoryRepository) {
    suspend operator fun invoke(category: Category): Result<Long> = repository.insertCategory(category)
}

class UpdateCategory(private val repository: CategoryRepository) {
    suspend operator fun invoke(category: Category): Result<Unit> = repository.updateCategory(category)
}

class DeleteCategory(private val repository: CategoryRepository) {
    suspend operator fun invoke(category: Category): Result<Unit> = repository.deleteCategory(category)
}
