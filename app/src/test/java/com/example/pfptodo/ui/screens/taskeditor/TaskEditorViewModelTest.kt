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
    fun `subtask batch failure keeps isSaved false and sets saveErrorRes`() = runTest {
        whenever(taskUseCases.addTask).thenReturn(mock())
        whenever(taskUseCases.addTask.invoke(any())).thenReturn(Result.Success(100L))
        whenever(taskUseCases.createSubtasksBatch).thenReturn(mock())
        whenever(taskUseCases.createSubtasksBatch.invoke(any(), any()))
            .thenReturn(Result.Error(RuntimeException("batch failed")))
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

        viewModel.onTitleChange("Task With Subtask")
        viewModel.onAddSubtask("Sub 1")
        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.saveErrorRes)
        verify(tagUseCases.setTaskTags, org.mockito.Mockito.never()).invoke(any(), any())
    }

    @Test
    fun `tag assignment failure keeps isSaved false and sets saveErrorRes`() = runTest {
        whenever(taskUseCases.addTask).thenReturn(mock())
        whenever(taskUseCases.addTask.invoke(any())).thenReturn(Result.Success(100L))
        whenever(tagUseCases.setTaskTags).thenReturn(mock())
        whenever(tagUseCases.setTaskTags.invoke(any(), any()))
            .thenReturn(Result.Error(RuntimeException("tags failed")))

        val viewModel = TaskEditorViewModel(
            taskId = null,
            parentTaskId = null,
            taskUseCases = taskUseCases,
            tagUseCases = tagUseCases,
            reminderManager = reminderManager
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onTitleChange("Task With Tags")
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

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorViewModelSubtaskReconcileTest {

    private val testDispatcher = StandardTestDispatcher()
    private val taskUseCases: TaskUseCases = mock()
    private val tagUseCases: TagUseCases = mock()
    private val reminderManager: ReminderManager = mock()

    private val parent = Task(id = 50L, title = "Parent")
    private val subA = Task(id = 11L, title = "Alpha", parentTaskId = 50L, orderIndex = 0)
    private val subB = Task(id = 12L, title = "Beta", parentTaskId = 50L, orderIndex = 1)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(tagUseCases.getAllTags).thenReturn(mock())
        whenever(tagUseCases.getAllTags.invoke()).thenReturn(flowOf(emptyList()))
        whenever(taskUseCases.getTaskById).thenReturn(mock())
        whenever(taskUseCases.getTaskById.invoke(50L)).thenReturn(Result.Success(parent))
        whenever(taskUseCases.getSubtasks).thenReturn(mock())
        whenever(taskUseCases.getSubtasks.invoke(50L)).thenReturn(flowOf(listOf(subA, subB)))
        whenever(taskUseCases.updateTask).thenReturn(mock())
        whenever(taskUseCases.updateTask.invoke(any())).thenReturn(Result.Success(Unit))
        whenever(tagUseCases.setTaskTags).thenReturn(mock())
        whenever(tagUseCases.setTaskTags.invoke(any(), any())).thenReturn(Result.Success(Unit))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editingViewModel(): TaskEditorViewModel {
        val vm = TaskEditorViewModel(50L, null, taskUseCases, tagUseCases, reminderManager)
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `editing loads existing subtasks as rows carrying their ids`() = runTest {
        val vm = editingViewModel()

        assertEquals(
            listOf(EditorSubtaskRow(id = 11L, title = "Alpha"), EditorSubtaskRow(id = 12L, title = "Beta")),
            vm.uiState.value.subtasks
        )
    }

    @Test
    fun `removing a loaded subtask deletes it on save instead of re-inserting everything`() = runTest {
        whenever(taskUseCases.deleteTask).thenReturn(mock())
        whenever(taskUseCases.deleteTask.invoke(any())).thenReturn(Result.Success(Unit))
        whenever(taskUseCases.createSubtasksBatch).thenReturn(mock())
        whenever(taskUseCases.createSubtasksBatch.invoke(any(), any()))
            .thenReturn(Result.Success(emptyList()))

        val vm = editingViewModel()
        vm.onRemoveSubtask(0)
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<Task>()
        verify(taskUseCases.deleteTask).invoke(captor.capture())
        assertEquals(11L, captor.firstValue.id)
        // The kept row must not be inserted again — no batch call at all.
        verify(taskUseCases.createSubtasksBatch, org.mockito.Mockito.never()).invoke(any(), any())
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `adding a row on edit batch-inserts only the new title`() = runTest {
        whenever(taskUseCases.createSubtasksBatch).thenReturn(mock())
        whenever(taskUseCases.createSubtasksBatch.invoke(any(), any()))
            .thenReturn(Result.Success(listOf(99L)))
        whenever(taskUseCases.deleteTask).thenReturn(mock())

        val vm = editingViewModel()
        vm.onAddSubtask("Gamma")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(taskUseCases.createSubtasksBatch).invoke(50L, listOf("Gamma"))
        verify(taskUseCases.deleteTask, org.mockito.Mockito.never()).invoke(any())
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `subtask delete failure fails the whole save`() = runTest {
        whenever(taskUseCases.deleteTask).thenReturn(mock())
        whenever(taskUseCases.deleteTask.invoke(any()))
            .thenReturn(Result.Error(RuntimeException("delete failed")))

        val vm = editingViewModel()
        vm.onRemoveSubtask(1)
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaved)
        assertNotNull(vm.uiState.value.saveErrorRes)
    }
}
