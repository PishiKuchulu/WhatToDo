package com.example.pfptodo.ui.screens.taskeditor

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.usecase.tag.TagUseCases
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val taskUseCases: TaskUseCases = mock()
    private val tagUseCases: TagUseCases = mock()
    private val reminderManager: ReminderManager = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(tagUseCases.getAllTags).thenReturn(mock())
        whenever(tagUseCases.getAllTags.invoke()).thenReturn(flowOf(listOf(
            Tag(id = 1L, name = "Work"),
            Tag(id = 2L, name = "Personal")
        )))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `creating a task saves title, priority and associates selected tags and updates isSaved`() = runTest {
        whenever(taskUseCases.addTask).thenReturn(mock())
        whenever(taskUseCases.addTask.invoke(any())).thenReturn(Result.Success(100L))
        whenever(tagUseCases.setTaskTags).thenReturn(mock())
        whenever(tagUseCases.setTaskTags.invoke(any(), any())).thenReturn(Result.Success(Unit))

        val viewModel = TaskEditorViewModel(
            taskId = null,
            parentTaskId = null,
            taskUseCases = taskUseCases,
            tagUseCases = tagUseCases,
            reminderManager = reminderManager
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onTitleChange("New Task")
        viewModel.onPriorityChange(Priority.HIGH)
        viewModel.onToggleTag(1L)
        viewModel.onToggleTag(2L)

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(taskUseCases.addTask).invoke(any())
        verify(tagUseCases.setTaskTags).invoke(100L, listOf(1L, 2L))
        assertTrue("isSaved must be true after persistence", viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `save failure sets saveError and keeps isSaved false`() = runTest {
        whenever(taskUseCases.addTask).thenReturn(mock())
        whenever(taskUseCases.addTask.invoke(any())).thenReturn(Result.Error(RuntimeException("Failed")))

        val viewModel = TaskEditorViewModel(
            taskId = null,
            parentTaskId = null,
            taskUseCases = taskUseCases,
            tagUseCases = tagUseCases,
            reminderManager = reminderManager
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onTitleChange("Failing Task")
        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.saveErrorRes)
    }

    @Test
    fun `blank title does not trigger save`() = runTest {
        val viewModel = TaskEditorViewModel(
            taskId = null,
            parentTaskId = null,
            taskUseCases = taskUseCases,
            tagUseCases = tagUseCases,
            reminderManager = reminderManager
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onTitleChange("   ")
        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `editing an existing task loads title and tags`() = runTest {
        val existingTask = Task(
            id = 50L,
            title = "Existing Task",
            tags = listOf(Tag(id = 1L, name = "Work"))
        )
        whenever(taskUseCases.getTaskById).thenReturn(mock())
        whenever(taskUseCases.getTaskById.invoke(50L)).thenReturn(Result.Success(existingTask))

        val viewModel = TaskEditorViewModel(
            taskId = 50L,
            parentTaskId = null,
            taskUseCases = taskUseCases,
            tagUseCases = tagUseCases,
            reminderManager = reminderManager
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Existing Task", viewModel.uiState.value.title)
        assertTrue(viewModel.uiState.value.selectedTagIds.contains(1L))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorViewModelEditPreservesDataTest {

    private val testDispatcher = StandardTestDispatcher()
    private val taskUseCases: TaskUseCases = mock()
    private val tagUseCases: TagUseCases = mock()
    private val reminderManager: ReminderManager = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(tagUseCases.getAllTags).thenReturn(mock())
        whenever(tagUseCases.getAllTags.invoke()).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `editing keeps notes, completion, parent, order and creation date`() = runTest {
        val created = java.time.Instant.parse("2026-01-01T00:00:00Z")
        val completedAt = java.time.Instant.parse("2026-02-01T00:00:00Z")
        val existing = Task(
            id = 7L, title = "Sub", notes = "keep me", isCompleted = true, completedAt = completedAt,
            parentTaskId = 3L, orderIndex = 4, createdAt = created
        )
        whenever(taskUseCases.getTaskById).thenReturn(mock())
        whenever(taskUseCases.getTaskById.invoke(7L)).thenReturn(Result.Success(existing))
        whenever(taskUseCases.updateTask).thenReturn(mock())
        whenever(taskUseCases.updateTask.invoke(any())).thenReturn(Result.Success(Unit))
        whenever(tagUseCases.setTaskTags).thenReturn(mock())
        whenever(tagUseCases.setTaskTags.invoke(any(), any())).thenReturn(Result.Success(Unit))

        val vm = TaskEditorViewModel(7L, null, taskUseCases, tagUseCases, reminderManager)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onTitleChange("Sub renamed")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<Task>()
        verify(taskUseCases.updateTask).invoke(captor.capture())
        val saved = captor.firstValue
        assertEquals("Sub renamed", saved.title)
        assertEquals("keep me", saved.notes)
        assertTrue(saved.isCompleted)
        assertEquals(completedAt, saved.completedAt)
        assertEquals(3L, saved.parentTaskId)
        assertEquals(4, saved.orderIndex)
        assertEquals(created, saved.createdAt)
    }
}
