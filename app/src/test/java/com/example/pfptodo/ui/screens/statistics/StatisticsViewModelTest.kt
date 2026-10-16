package com.example.pfptodo.ui.screens.statistics

import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.usecase.category.CategoryUseCases
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val taskUseCases: TaskUseCases = mock()
    private val categoryUseCases: CategoryUseCases = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun statisticsFor(tasks: List<Task>): StatisticsUiState {
        val getFiltered = mock<com.example.pfptodo.domain.usecase.task.GetFilteredTasksUseCase>()
        whenever(taskUseCases.getFilteredTasks).thenReturn(getFiltered)
        whenever(getFiltered.invoke("", TaskFilter(), SortOrder.CREATION_DATE)).thenReturn(flowOf(tasks))

        // Create an in-memory CategoryRepository so the real GetAllCategories use case works.
        val categoryRepo = object : com.example.pfptodo.domain.repository.CategoryRepository {
            override fun getAllCategories() = flowOf(emptyList<Category>())
            override suspend fun getAllCategoriesRaw() = emptyList<Category>()
            override suspend fun getCategoryById(id: Long) = com.example.pfptodo.core.util.Result.Success(null)
            override suspend fun insertCategory(category: Category) = com.example.pfptodo.core.util.Result.Success(1L)
            override suspend fun updateCategory(category: Category) = com.example.pfptodo.core.util.Result.Success(Unit)
            override suspend fun deleteCategory(category: Category) = com.example.pfptodo.core.util.Result.Success(Unit)
        }
        val getAllCats = com.example.pfptodo.domain.usecase.category.GetAllCategories(categoryRepo)
        whenever(categoryUseCases.getAllCategories).thenReturn(getAllCats)

        val vm = StatisticsViewModel(taskUseCases, categoryUseCases)
        // StateFlow.first() returns the initialValue immediately. Use drop(1).first()
        // to skip the initial empty state and wait for the actual computed value.
        return vm.uiState.drop(1).first()
    }

    private fun atHour(hour: Int): Instant =
        LocalDate.now(ZoneId.systemDefault()).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant()

    @Test
    fun `task due earlier today is not counted as overdue`() = runTest {
        val dueTodayMorning = Task(
            id = 1,
            title = "Due Today",
            priority = Priority.MEDIUM,
            dueDate = atHour(9),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val dueYesterday = Task(
            id = 2,
            title = "Due Yesterday",
            priority = Priority.MEDIUM,
            dueDate = atHour(9).minusSeconds(24 * 3600),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val state = statisticsFor(listOf(dueTodayMorning, dueYesterday))

        assertEquals("only the yesterday task is overdue", 1, state.overdueTasks)
        assertEquals("total counts both", 2, state.totalTasks)
    }

    @Test
    fun `completed task is not overdue even if due in the past`() = runTest {
        val completed = Task(
            id = 3,
            title = "Done",
            isCompleted = true,
            completedAt = Instant.now().minusSeconds(7200),
            priority = Priority.LOW,
            dueDate = Instant.now().minusSeconds(86400),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val state = statisticsFor(listOf(completed))

        assertEquals(0, state.overdueTasks)
        assertEquals(1, state.completedTasks)
    }

    @Test
    fun `empty dataset yields zero metrics`() = runTest {
        val state = statisticsFor(emptyList())
        assertEquals(0, state.totalTasks)
        assertEquals(0, state.overdueTasks)
        assertEquals(0, state.completionRate)
        assertTrue(state.completionTrend.size == 7)
    }
}