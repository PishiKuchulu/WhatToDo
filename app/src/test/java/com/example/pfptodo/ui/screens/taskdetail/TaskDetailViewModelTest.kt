package com.example.pfptodo.ui.screens.taskdetail

import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.AttachmentRepository
import com.example.pfptodo.domain.usecase.task.ObserveTaskById
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import com.example.pfptodo.domain.usecase.task.ToggleTaskCompletionUseCase
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val taskUseCases: TaskUseCases = mock()
    private val observeTaskById: ObserveTaskById = mock()
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase = mock()
    private val attachmentRepository: AttachmentRepository = mock()
    private val reminderManager: ReminderManager = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(taskUseCases.observeTaskById).thenReturn(observeTaskById)
        whenever(taskUseCases.toggleTaskCompletion).thenReturn(toggleTaskCompletionUseCase)
        whenever(taskUseCases.getSubtasks).thenReturn(mock())
        whenever(taskUseCases.getSubtasks.invoke(1L)).thenReturn(flowOf(emptyList()))
        whenever(attachmentRepository.getAttachmentsForTask(1L)).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `task changed in repository automatically updates TaskDetailViewModel uiState`() = runTest {
        val initialTask = Task(id = 1L, title = "Original Title", description = "Original Desc")
        val taskFlow = MutableStateFlow<Task?>(initialTask)
        whenever(observeTaskById.invoke(1L)).thenReturn(taskFlow)

        val viewModel = TaskDetailViewModel(
            taskId = 1L,
            taskUseCases = taskUseCases,
            attachmentRepository = attachmentRepository,
            reminderManager = reminderManager
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Original Title", viewModel.uiState.value.task?.title)
        assertFalse(viewModel.uiState.value.isLoading)

        // Simulate external edit (e.g. from TaskEditorScreen updating Room)
        val editedTask = initialTask.copy(title = "Edited Title", description = "Updated Desc")
        taskFlow.value = editedTask
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Edited Title", viewModel.uiState.value.task?.title)
        assertEquals("Updated Desc", viewModel.uiState.value.task?.description)

        collectJob.cancel()
    }

    @Test
    fun `missing or deleted task emits null task state gracefully`() = runTest {
        val taskFlow = MutableStateFlow<Task?>(null)
        whenever(observeTaskById.invoke(1L)).thenReturn(taskFlow)

        val viewModel = TaskDetailViewModel(
            taskId = 1L,
            taskUseCases = taskUseCases,
            attachmentRepository = attachmentRepository,
            reminderManager = reminderManager
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.task)
        assertFalse(viewModel.uiState.value.isLoading)

        collectJob.cancel()
    }

    @Test
    fun `onToggleTaskCompletion delegates to ToggleTaskCompletionUseCase`() = runTest {
        val currentTask = Task(id = 1L, title = "Complete Me", isCompleted = false)
        whenever(observeTaskById.invoke(1L)).thenReturn(MutableStateFlow(currentTask))

        val viewModel = TaskDetailViewModel(
            taskId = 1L,
            taskUseCases = taskUseCases,
            attachmentRepository = attachmentRepository,
            reminderManager = reminderManager
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onToggleTaskCompletion()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(taskUseCases.toggleTaskCompletion).invoke(currentTask)
        collectJob.cancel()
    }
}
