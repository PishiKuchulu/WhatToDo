package com.example.pfptodo.ui.screens.calendar

import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.domain.usecase.calendar.CalculateDailyLoadDensityUseCase
import com.example.pfptodo.domain.usecase.calendar.CalculateRoutineStreaksUseCase
import com.example.pfptodo.domain.usecase.calendar.GetSevenDayPlanningUseCase
import com.example.pfptodo.domain.usecase.task.GetFilteredTasksUseCase
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import com.example.pfptodo.domain.usecase.task.ToggleTaskCompletionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val taskUseCases: TaskUseCases = mock()
    private val getFilteredTasksUseCase: GetFilteredTasksUseCase = mock()
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase = mock()
    private val calculateDailyLoadDensityUseCase = CalculateDailyLoadDensityUseCase()
    private val getSevenDayPlanningUseCase = GetSevenDayPlanningUseCase(calculateDailyLoadDensityUseCase)
    private val calculateRoutineStreaksUseCase = CalculateRoutineStreaksUseCase()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(taskUseCases.getFilteredTasks).thenReturn(getFilteredTasksUseCase)
        whenever(taskUseCases.toggleTaskCompletion).thenReturn(toggleTaskCompletionUseCase)
        whenever(taskUseCases.getSevenDayPlanning).thenReturn(getSevenDayPlanningUseCase)
        whenever(taskUseCases.calculateDailyLoadDensity).thenReturn(calculateDailyLoadDensityUseCase)
        whenever(taskUseCases.calculateRoutineStreaks).thenReturn(calculateRoutineStreaksUseCase)

        whenever(getFilteredTasksUseCase.invoke("", TaskFilter(status = TaskFilter.Status.ALL), SortOrder.DUE_DATE))
            .thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `date and month selection updates state`() {
        val viewModel = CalendarViewModel(taskUseCases)
        val targetDate = LocalDate.of(2026, 8, 20)
        viewModel.onDateSelected(targetDate)
        assertEquals(targetDate, viewModel.selectedDate.value)

        val targetMonth = LocalDate.of(2026, 9, 15)
        viewModel.onMonthChange(targetMonth)
        assertEquals(LocalDate.of(2026, 9, 1), viewModel.currentMonth.value)
    }

    @Test
    fun `selecting date in another month automatically shifts currentMonth`() {
        val viewModel = CalendarViewModel(taskUseCases)
        val futureMonthDate = LocalDate.of(2026, 10, 5)
        viewModel.onDateSelected(futureMonthDate)
        assertEquals(futureMonthDate, viewModel.selectedDate.value)
        assertEquals(LocalDate.of(2026, 10, 1), viewModel.currentMonth.value)
    }

    @Test
    fun `uiState emits sevenDayPlan and routine streaks reactively`() = runTest {
        val task1 = Task(
            id = 1,
            title = "Daily Standup",
            recurrenceRule = RecurrenceRule.Daily,
            isCompleted = true,
            completedAt = Instant.now()
        )
        whenever(getFilteredTasksUseCase.invoke("", TaskFilter(status = TaskFilter.Status.ALL), SortOrder.DUE_DATE))
            .thenReturn(flowOf(listOf(task1)))

        val viewModel = CalendarViewModel(taskUseCases)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(7, state.sevenDayPlan.size)
        assertEquals(1, state.streakSummary.totalActiveRoutines)
        assertEquals(1, state.streakSummary.routines[0].currentStreak)
        assertEquals("Daily Standup", state.streakSummary.routines[0].title)

        collectJob.cancel()
    }

    @Test
    fun `onToggleTaskCompletion delegates to ToggleTaskCompletionUseCase`() = runTest {
        val viewModel = CalendarViewModel(taskUseCases)
        val task = Task(id = 10L, title = "Calendar Task", isCompleted = false)

        viewModel.onToggleTaskCompletion(task)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(taskUseCases.toggleTaskCompletion).invoke(task)
    }
}
