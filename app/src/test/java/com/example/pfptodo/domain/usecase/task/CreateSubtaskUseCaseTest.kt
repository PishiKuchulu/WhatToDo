package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.flowOf
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

class CreateSubtaskUseCaseTest {

    private lateinit var useCase: CreateSubtaskUseCase
    private val repository: TaskRepository = mock()

    @Before
    fun setup() {
        useCase = CreateSubtaskUseCase(repository)
    }

    @Test
    fun `creates subtask successfully under main task with correct parentId and order`() = runBlocking {
        val parentTask = Task(id = 10, title = "Main Parent Task", parentTaskId = null)
        val existingSubtask = Task(id = 101, title = "Existing Subtask", parentTaskId = 10, orderIndex = 0)

        whenever(repository.getTaskById(10)).thenReturn(Result.Success(parentTask))
        whenever(repository.getSubtasksForTask(10)).thenReturn(flowOf(listOf(existingSubtask)))
        whenever(repository.insertTask(any())).thenReturn(Result.Success(102L))

        val result = useCase(parentId = 10, title = "New Subtask")
        assertTrue(result is Result.Success)
        assertEquals(102L, (result as Result.Success).data)

        val captor = argumentCaptor<Task>()
        verify(repository).insertTask(captor.capture())

        val inserted = captor.firstValue
        assertEquals("New Subtask", inserted.title)
        assertEquals(10L, inserted.parentTaskId)
        assertEquals(1, inserted.orderIndex)
    }

    @Test
    fun `rejects adding subtask when title is blank`() = runBlocking {
        val result = useCase(parentId = 10, title = "   ")
        assertTrue(result is Result.Error)
    }

    @Test
    fun `rejects adding subtask when parent task not found`() = runBlocking {
        whenever(repository.getTaskById(999)).thenReturn(Result.Success(null))

        val result = useCase(parentId = 999, title = "Subtask")
        assertTrue(result is Result.Error)
    }

    @Test
    fun `enforces 1-level depth limit by rejecting subtask under another subtask`() = runBlocking {
        val childTask = Task(id = 20, title = "Child Subtask", parentTaskId = 10L)
        whenever(repository.getTaskById(20)).thenReturn(Result.Success(childTask))

        val result = useCase(parentId = 20, title = "Sub-subtask")
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is IllegalStateException)
    }
}
