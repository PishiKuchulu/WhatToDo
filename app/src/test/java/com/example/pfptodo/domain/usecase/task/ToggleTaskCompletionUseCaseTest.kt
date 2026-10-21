package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TagRepository
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.util.RecurrenceEngine
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.time.temporal.ChronoUnit

class ToggleTaskCompletionUseCaseTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var reminderManager: ReminderManager
    private lateinit var tagRepository: TagRepository
    private lateinit var useCase: ToggleTaskCompletionUseCase

    @Before
    fun setup() {
        taskRepository = mock()
        reminderManager = mock()
        tagRepository = mock()
        useCase = ToggleTaskCompletionUseCase(taskRepository, reminderManager, tagRepository)
    }

    @Test
    fun `completing uncompleted task marks completed, sets completedAt, and cancels reminder`() = runTest {
        val originalTask = Task(
            id = 42L,
            title = "Buy groceries",
            isCompleted = false,
            completedAt = null,
            reminderTime = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Success(Unit))

        val result = useCase(originalTask)

        assertTrue(result is Result.Success)
        verify(taskRepository).updateTask(check { updated ->
            assertEquals(42L, updated.id)
            assertTrue(updated.isCompleted)
            assertNotNull(updated.completedAt)
        })
        verify(reminderManager).cancelReminder(42L)
        verify(reminderManager, never()).scheduleReminder(any())
    }

    @Test
    fun `uncompleting completed task clears completedAt and reschedules reminder`() = runTest {
        val reminderInstant = Instant.now().plus(2, ChronoUnit.DAYS)
        val originalTask = Task(
            id = 42L,
            title = "Buy groceries",
            isCompleted = true,
            completedAt = Instant.now().minus(1, ChronoUnit.HOURS),
            reminderTime = reminderInstant
        )
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Success(Unit))

        val result = useCase(originalTask)

        assertTrue(result is Result.Success)
        verify(taskRepository).updateTask(check { updated ->
            assertEquals(42L, updated.id)
            assertFalse(updated.isCompleted)
            assertNull(updated.completedAt)
        })
        verify(reminderManager, never()).cancelReminder(any())
        verify(reminderManager).scheduleReminder(check { scheduled ->
            assertEquals(42L, scheduled.id)
            assertFalse(scheduled.isCompleted)
            assertEquals(reminderInstant, scheduled.reminderTime)
        })
    }

    @Test
    fun `completing recurring task spawns next occurrence and schedules its reminder`() = runTest {
        val now = Instant.now()
        val originalTask = Task(
            id = 10L,
            title = "Weekly Review",
            isCompleted = false,
            recurrenceRule = RecurrenceRule.Weekly,
            dueDate = now,
            recurrenceAnchor = now,
            reminderTime = now
        )
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Success(Unit))
        whenever(taskRepository.insertTask(any())).thenReturn(Result.Success(101L))
        // The anti-duplicate guard scans raw tasks; stub it so the real branch runs.
        whenever(taskRepository.getAllTasksRaw()).thenReturn(emptyList())
        whenever(taskRepository.getSubtasksForTask(any())).thenReturn(flowOf(emptyList()))

        val result = useCase(originalTask)

        assertTrue(result is Result.Success)
        verify(taskRepository).updateTask(check { updated ->
            assertTrue(updated.isCompleted)
        })
        verify(reminderManager).cancelReminder(10L)

        // Spawns next recurring task
        verify(taskRepository).insertTask(check { spawned ->
            assertEquals(0L, spawned.id)
            assertFalse(spawned.isCompleted)
            assertEquals(RecurrenceRule.Weekly, spawned.recurrenceRule)
            assertEquals(now, spawned.recurrenceAnchor)
            assertNotNull(spawned.dueDate)
            assertNotNull(spawned.reminderTime)
        })

        // Reminder scheduled for newly spawned task with ID 101
        verify(reminderManager).scheduleReminder(check { scheduled ->
            assertEquals(101L, scheduled.id)
            assertFalse(scheduled.isCompleted)
        })
    }

    @Test
    fun `completing recurring task without reminder does not schedule reminder for next occurrence`() = runTest {
        val now = Instant.now()
        val originalTask = Task(
            id = 20L,
            title = "Water Plants",
            isCompleted = false,
            recurrenceRule = RecurrenceRule.Daily,
            dueDate = now,
            recurrenceAnchor = now,
            reminderTime = null
        )
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Success(Unit))
        whenever(taskRepository.insertTask(any())).thenReturn(Result.Success(102L))
        whenever(taskRepository.getAllTasksRaw()).thenReturn(emptyList())
        whenever(taskRepository.getSubtasksForTask(any())).thenReturn(flowOf(emptyList()))

        val result = useCase(originalTask)

        assertTrue(result is Result.Success)
        verify(taskRepository).insertTask(check { spawned ->
            assertNull(spawned.reminderTime)
        })
        verify(reminderManager, never()).scheduleReminder(any())
    }

    @Test
    fun `repository update failure returns error and does not spawn recurrence`() = runTest {
        val originalTask = Task(
            id = 30L,
            title = "Test Error",
            isCompleted = false,
            recurrenceRule = RecurrenceRule.Daily
        )
        val expectedError = RuntimeException("Database write failed")
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Error(expectedError))

        val result = useCase(originalTask)

        assertTrue(result is Result.Error)
        assertEquals(expectedError, (result as Result.Error).exception)
        verify(taskRepository, never()).insertTask(any())
        verify(reminderManager, never()).cancelReminder(any())
    }

    @Test
    fun `completing recurring task copies subtasks onto the spawned occurrence`() = runTest {
        val now = Instant.now()
        val originalTask = Task(
            id = 50L,
            title = "Weekly Review",
            isCompleted = false,
            recurrenceRule = RecurrenceRule.Weekly,
            dueDate = now,
            recurrenceAnchor = now
        )
        val subtasks = listOf(
            Task(id = 51L, title = "Sub A", parentTaskId = 50L),
            Task(id = 52L, title = "Sub B", parentTaskId = 50L, isCompleted = true)
        )
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Success(Unit))
        whenever(taskRepository.getAllTasksRaw()).thenReturn(emptyList())
        whenever(taskRepository.getSubtasksForTask(eq(50L))).thenReturn(flowOf(subtasks))
        whenever(taskRepository.insertTask(any())).thenReturn(Result.Success(150L))

        val result = useCase(originalTask)

        assertTrue(result is Result.Success)
        // One insert for the occurrence + one per copied subtask.
        verify(taskRepository, times(3)).insertTask(any())
        // The two copied subtasks: fresh id, reparented to the spawned
        // occurrence (150), reset to uncompleted. The strict title assertion
        // keeps the occurrence's own insert from matching this verify.
        verify(taskRepository, times(2)).insertTask(check { copied ->
            assertTrue(copied.title == "Sub A" || copied.title == "Sub B")
            assertEquals(0L, copied.id)
            assertEquals(150L, copied.parentTaskId)
            assertFalse(copied.isCompleted)
            assertNull(copied.completedAt)
        })
    }

    @Test
    fun `completing recurring task does not spawn a duplicate when the next occurrence already exists`() = runTest {
        val now = Instant.now()
        val originalTask = Task(
            id = 60L,
            title = "Standup",
            isCompleted = false,
            recurrenceRule = RecurrenceRule.Daily,
            dueDate = now,
            recurrenceAnchor = now
        )
        // The occurrence the guard looks for: same title/rule/anchor and the
        // exact next due date the engine would compute.
        val expectedNextDue = RecurrenceEngine.calculateNextOccurrence(now, now, RecurrenceRule.Daily)
        val alreadySpawned = Task(
            id = 61L,
            title = "Standup",
            isCompleted = false,
            parentTaskId = null,
            recurrenceRule = RecurrenceRule.Daily,
            recurrenceAnchor = now,
            dueDate = expectedNextDue
        )
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Success(Unit))
        whenever(taskRepository.getAllTasksRaw()).thenReturn(listOf(originalTask, alreadySpawned))

        val result = useCase(originalTask)

        assertTrue(result is Result.Success)
        verify(taskRepository, never()).insertTask(any())
        verify(reminderManager, never()).scheduleReminder(any())
    }

    @Test
    fun `completing recurring task preserves tags on the spawned occurrence`() = runTest {
        val now = Instant.now()
        val originalTask = Task(
            id = 70L,
            title = "Gym",
            isCompleted = false,
            recurrenceRule = RecurrenceRule.Weekly,
            dueDate = now,
            recurrenceAnchor = now,
            tags = listOf(Tag(id = 5L, name = "Health"), Tag(id = 9L, name = "Routine"))
        )
        whenever(taskRepository.updateTask(any())).thenReturn(Result.Success(Unit))
        whenever(taskRepository.getAllTasksRaw()).thenReturn(emptyList())
        whenever(taskRepository.getSubtasksForTask(any())).thenReturn(flowOf(emptyList()))
        whenever(taskRepository.insertTask(any())).thenReturn(Result.Success(170L))
        whenever(tagRepository.setTaskTags(any(), any())).thenReturn(Result.Success(Unit))

        val result = useCase(originalTask)

        assertTrue(result is Result.Success)
        verify(tagRepository).setTaskTags(eq(170L), eq(listOf(5L, 9L)))
    }
}
