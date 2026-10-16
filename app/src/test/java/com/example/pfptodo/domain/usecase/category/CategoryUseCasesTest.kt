package com.example.pfptodo.domain.usecase.category

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CategoryUseCasesTest {

    private lateinit var fakeRepo: FakeCategoryRepository
    private lateinit var categoryUseCases: CategoryUseCases

    @Before
    fun setUp() {
        fakeRepo = FakeCategoryRepository()
        categoryUseCases = CategoryUseCases(
            getAllCategories = GetAllCategories(fakeRepo),
            getCategoryById = GetCategoryById(fakeRepo),
            addCategory = AddCategory(fakeRepo),
            updateCategory = UpdateCategory(fakeRepo),
            deleteCategory = DeleteCategory(fakeRepo)
        )
    }

    @Test
    fun addCategory_returnsGeneratedId_andAppearsInFlow() = runBlocking {
        val result = categoryUseCases.addCategory(Category(name = "Work", color = "#2196F3", iconName = "work"))

        assertTrue(result is Result.Success)
        val id = (result as Result.Success).data
        assertTrue(id > 0L)

        val categories = categoryUseCases.getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("Work", categories.first().name)
    }

    @Test
    fun getCategoryById_returnsCategory_whenPresent() = runBlocking {
        val id = (categoryUseCases.addCategory(Category(name = "Home", color = "#4CAF50", iconName = "home")) as Result.Success).data

        val result = categoryUseCases.getCategoryById(id)

        assertTrue(result is Result.Success)
        assertEquals("Home", (result as Result.Success).data?.name)
    }

    @Test
    fun getCategoryById_returnsNull_whenAbsent() = runBlocking {
        val result = categoryUseCases.getCategoryById(999L)

        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun updateCategory_persistsChanges() = runBlocking {
        val id = (categoryUseCases.addCategory(Category(name = "Old", color = "#000000", iconName = "x")) as Result.Success).data

        val result = categoryUseCases.updateCategory(Category(id = id, name = "New", color = "#FFFFFF", iconName = "y"))

        assertTrue(result is Result.Success)
        val fetched = categoryUseCases.getCategoryById(id) as Result.Success
        assertEquals("New", fetched.data?.name)
        assertEquals("#FFFFFF", fetched.data?.color)
    }

    @Test
    fun updateCategory_returnsError_forUnknownId() = runBlocking {
        val result = categoryUseCases.updateCategory(Category(id = 12345L, name = "Ghost", color = "#000", iconName = "x"))

        assertTrue(result is Result.Error)
    }

    @Test
    fun deleteCategory_removesIt() = runBlocking {
        val id = (categoryUseCases.addCategory(Category(name = "Temp", color = "#999999", iconName = "t")) as Result.Success).data
        val category = Category(id = id, name = "Temp", color = "#999999", iconName = "t")

        val result = categoryUseCases.deleteCategory(category)

        assertTrue(result is Result.Success)
        assertTrue(categoryUseCases.getAllCategories().first().isEmpty())
    }

    // ------------------------------------------------------------------

    private class FakeCategoryRepository : CategoryRepository {
        private val categories = mutableListOf<Category>()
        private val flow = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        private fun refresh() {
            flow.value = categories.toList()
        }

        override fun getAllCategories(): Flow<List<Category>> = flow

        override suspend fun getAllCategoriesRaw(): List<Category> = categories.toList()

        override suspend fun getCategoryById(id: Long): Result<Category?> =
            Result.Success(categories.find { it.id == id })

        override suspend fun insertCategory(category: Category): Result<Long> {
            val id = nextId++
            categories.add(category.copy(id = id))
            refresh()
            return Result.Success(id)
        }

        override suspend fun updateCategory(category: Category): Result<Unit> {
            val index = categories.indexOfFirst { it.id == category.id }
            if (index < 0) return Result.Error(NoSuchElementException("Category ${category.id} not found"))
            categories[index] = category
            refresh()
            return Result.Success(Unit)
        }

        override suspend fun deleteCategory(category: Category): Result<Unit> {
            categories.removeAll { it.id == category.id }
            refresh()
            return Result.Success(Unit)
        }
    }
}
