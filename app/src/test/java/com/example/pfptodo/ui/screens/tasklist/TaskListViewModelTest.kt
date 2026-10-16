package com.example.pfptodo.ui.screens.tasklist

import com.example.pfptodo.core.repository.ThemeRepository
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.domain.parser.LocalTaskParser
import com.example.pfptodo.domain.usecase.tag.TagUseCases
import com.example.pfptodo.domain.usecase.task.GetFilteredTasksUseCase
import com.example.pfptodo.domain.usecase.task.ParseSmartTaskUseCase
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import com.example.pfptodo.domain.usecase.task.ToggleTaskCompletionUseCase
import com.example.pfptodo.ui.theme.AppTheme
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TaskListViewModel
    private val getFilteredTasksUseCase: GetFilteredTasksUseCase = mock()
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase = mock()
    private val taskUseCases: TaskUseCases = mock()
    private val tagUseCases: TagUseCases = mock()
    private val parseSmartTaskUseCase = ParseSmartTaskUseCase(LocalTaskParser())
    private val themeRepository: ThemeRepository = mock()
    private val reminderManager: ReminderManager = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(themeRepository.themeFlow).thenReturn(flowOf(AppTheme.SYSTEM))
        whenever(taskUseCases.getFilteredTasks).thenReturn(getFilteredTasksUseCase)
        whenever(taskUseCases.toggleTaskCompletion).thenReturn(toggleTaskCompletionUseCase)
        whenever(taskUseCases.getSmartSuggestions).thenReturn(com.example.pfptodo.domain.usecase.task.GetSmartSuggestionsUseCase())
        whenever(taskUseCases.parseNaturalLanguageSearch).thenReturn(com.example.pfptodo.domain.usecase.task.ParseNaturalLanguageSearchUseCase())
        whenever(tagUseCases.getAllTags).thenReturn(mock())
        whenever(tagUseCases.getAllTags.invoke()).thenReturn(flowOf(listOf(Tag(id = 1L, name = "Work"))))
        whenever(getFilteredTasksUseCase.invoke(any(), any(), any()))
            .thenReturn(flowOf(emptyList()))

        viewModel = TaskListViewModel(taskUseCases, tagUseCases, parseSmartTaskUseCase, themeRepository, reminderManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(emptyList<Task>(), state.tasks)
        assertEquals(AppTheme.SYSTEM, state.appTheme)
    }

    @Test
    fun `toggle tag filter adds and removes tag from filter`() = runTest {
        viewModel.onTagFilterToggle(1L)
        assertEquals(setOf(1L), viewModel.filter.value.tagIds)

        viewModel.onTagFilterToggle(2L)
        assertEquals(setOf(1L, 2L), viewModel.filter.value.tagIds)

        viewModel.onTagFilterToggle(1L)
        assertEquals(setOf(2L), viewModel.filter.value.tagIds)

        viewModel.onClearTagFilter()
        assertTrue(viewModel.filter.value.tagIds.isEmpty())
    }

    @Test
    fun `changing search query emits updated task list reactively`() = runTest {
        val searchResultTask = Task(id = 10L, title = "Search Match")
        whenever(getFilteredTasksUseCase.invoke("Search", TaskFilter(), SortOrder.MANUAL))
            .thenReturn(flowOf(listOf(searchResultTask)))

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onSearchQueryChange("Search")
        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()

        assertEquals(listOf(searchResultTask), viewModel.uiState.value.tasks)
        collectJob.cancel()
    }

    @Test
    fun `changing filter immediately emits updated task list reactively`() = runTest {
        val priorityFilter = TaskFilter(priorities = setOf(Priority.HIGH))
        val highPriorityTask = Task(id = 20L, title = "High Priority Task", priority = Priority.HIGH)
        whenever(getFilteredTasksUseCase.invoke("", priorityFilter, SortOrder.MANUAL))
            .thenReturn(flowOf(listOf(highPriorityTask)))

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onFilterChange(priorityFilter)
        testScheduler.runCurrent()

        assertEquals(listOf(highPriorityTask), viewModel.uiState.value.tasks)
        assertEquals(priorityFilter, viewModel.uiState.value.filter)
        collectJob.cancel()
    }

    @Test
    fun `changing sort order immediately emits updated task list reactively`() = runTest {
        val sortedTask = Task(id = 30L, title = "Sorted Task")
        whenever(getFilteredTasksUseCase.invoke("", TaskFilter(), SortOrder.DUE_DATE))
            .thenReturn(flowOf(listOf(sortedTask)))

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onSortOrderChange(SortOrder.DUE_DATE)
        testScheduler.runCurrent()

        assertEquals(listOf(sortedTask), viewModel.uiState.value.tasks)
        assertEquals(SortOrder.DUE_DATE, viewModel.uiState.value.sortOrder)
        collectJob.cancel()
    }

    @Test
    fun `toggling tag filter immediately emits updated task list reactively`() = runTest {
        val tagFilter = TaskFilter(tagIds = setOf(1L))
        val taggedTask = Task(id = 40L, title = "Tagged Task")
        whenever(getFilteredTasksUseCase.invoke("", tagFilter, SortOrder.MANUAL))
            .thenReturn(flowOf(listOf(taggedTask)))

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onTagFilterToggle(1L)
        testScheduler.runCurrent()

        assertEquals(listOf(taggedTask), viewModel.uiState.value.tasks)
        assertEquals(setOf(1L), viewModel.uiState.value.filter.tagIds)
        collectJob.cancel()
    }

    @Test
    fun `onToggleTaskCompletion delegates to taskUseCases toggleTaskCompletion`() = runTest {
        val task = Task(id = 50L, title = "Test Task")
        viewModel.onToggleTaskCompletion(task)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(taskUseCases.toggleTaskCompletion).invoke(task)
    }

    @Test
    fun `parseSmartInput delegates to ParseSmartTaskUseCase`() {
        val parsed = viewModel.parseSmartInput("Buy milk tomorrow #Shopping")
        assertEquals("Buy milk", parsed.title)
        assertEquals(listOf("Shopping"), parsed.tags)
    }

    @Test
    fun `smart suggestions are exposed in uiState and dismissable`() = runTest {
        val overdueHighTask = Task(
            id = 99L,
            title = "Urgent Server Migration",
            priority = Priority.HIGH,
            dueDate = java.time.Instant.parse("2026-08-01T10:00:00Z")
        )
        val tasksFlow = kotlinx.coroutines.flow.MutableStateFlow(listOf(overdueHighTask))
        whenever(getFilteredTasksUseCase.invoke("", TaskFilter(status = TaskFilter.Status.ALL), SortOrder.MANUAL))
            .thenReturn(tasksFlow)

        val vm = TaskListViewModel(taskUseCases, tagUseCases, parseSmartTaskUseCase, themeRepository, reminderManager)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        testScheduler.runCurrent()
        assertEquals(1, vm.uiState.value.suggestions.size)
        assertEquals(99L, vm.uiState.value.suggestions[0].relatedTaskId)

        // Dismiss suggestion
        vm.onDismissSuggestion(vm.uiState.value.suggestions[0].id)
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.suggestions.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `natural language search query parses intent and decomposes into chips and merged filter`() = runTest {
        val task = Task(id = 100L, title = "Finish project", priority = Priority.HIGH)
        whenever(getFilteredTasksUseCase.invoke(
            "",
            TaskFilter(priorities = setOf(Priority.HIGH), dateFilter = TaskFilter.DateFilter.THIS_WEEK),
            SortOrder.MANUAL
        )).thenReturn(flowOf(listOf(task)))

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onSearchQueryChange("important tasks this week")
        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()

        assertEquals(listOf(task), viewModel.uiState.value.tasks)
        assertEquals(2, viewModel.uiState.value.parsedSearch.chips.size)
        assertEquals("", viewModel.uiState.value.parsedSearch.keyword)

        // Dismiss chip clears search
        viewModel.onRemoveSearchChip(viewModel.uiState.value.parsedSearch.chips[0])
        testScheduler.runCurrent()
        assertEquals("", viewModel.searchQuery.value)

        collectJob.cancel()
    }
}
