package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneId

class RescheduleTaskUseCaseTest {

    private lateinit var useCase: RescheduleTaskUseCase
    private val repository: TaskRepository = mock()
    private val reminderManager: ReminderManager = mock()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    @Before
    fun setup() {
        useCase = RescheduleTaskUseCase(repository, reminderManager)
    }

    @Test
    fun `snoozeDays adds 1 day to existing due date`() = runBlocking {
        val originalDue = today.atTime(15, 30).atZone(zone).toInstant()
        val task = Task(id = 1, title = "Task", dueDate = originalDue)
        whenever(repository.updateTask(any())).thenReturn(Result.Success(Unit))

        val result = useCase.snoozeDays(task, 1)
        assertTrue(result is Result.Success)

        val captor = argumentCaptor<Task>()
        verify(repository).updateTask(captor.capture())

        val updatedTask = captor.firstValue
        val expectedDate = today.plusDays(1)
        assertEquals(expectedDate, updatedTask.dueDate?.atZone(zone)?.toLocalDate())
        assertEquals(15, updatedTask.dueDate?.atZone(zone)?.hour)
        assertEquals(30, updatedTask.dueDate?.atZone(zone)?.minute)
    }

    @Test
    fun `snoozeDays on overdue task reschedules to tomorrow`() = runBlocking {
        val overdueDue = today.minusDays(5).atTime(10, 0).atZone(zone).toInstant()
        val task = Task(id = 2, title = "Overdue Task", dueDate = overdueDue)
        whenever(repository.updateTask(any())).thenReturn(Result.Success(Unit))

        val result = useCase.snoozeDays(task, 1)
        assertTrue(result is Result.Success)

        val captor = argumentCaptor<Task>()
        verify(repository).updateTask(captor.capture())

        val updatedTask = captor.firstValue
        val expectedDate = today.plusDays(1)
        assertEquals(expectedDate, updatedTask.dueDate?.atZone(zone)?.toLocalDate())
    }

    @Test
    fun `snoozeDays on task without due date sets tomorrow 9am`() = runBlocking {
        val task = Task(id = 3, title = "No Date Task", dueDate = null)
        whenever(repository.updateTask(any())).thenReturn(Result.Success(Unit))

        val result = useCase.snoozeDays(task, 1)
        assertTrue(result is Result.Success)

        val captor = argumentCaptor<Task>()
        verify(repository).updateTask(captor.capture())

        val updatedTask = captor.firstValue
        val expectedDate = today.plusDays(1)
        assertEquals(expectedDate, updatedTask.dueDate?.atZone(zone)?.toLocalDate())
        assertEquals(9, updatedTask.dueDate?.atZone(zone)?.hour)
        assertEquals(0, updatedTask.dueDate?.atZone(zone)?.minute)
    }
}
