package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddTaskTest {

    private lateinit var useCase: AddTask
    private val repository: TaskRepository = mock()

    @Before
    fun setup() {
        useCase = AddTask(repository)
    }

    @Test
    fun `top-level task is inserted directly`() = runBlocking {
        whenever(repository.insertTask(any())).thenReturn(Result.Success(1L))

        val result = useCase(Task(title = "Top level"))

        assertTrue(result is Result.Success)
        assertEquals(1L, (result as Result.Success).data)
        verify(repository).insertTask(any())
    }

    @Test
    fun `subtask under a top-level parent is inserted`() = runBlocking {
        val parentTask = Task(id = 10, title = "Parent", parentTaskId = null)
        whenever(repository.getTaskById(10)).thenReturn(Result.Success(parentTask))
        whenever(repository.insertTask(any())).thenReturn(Result.Success(11L))

        val result = useCase(Task(title = "Subtask", parentTaskId = 10))

        assertTrue(result is Result.Success)
        assertEquals(11L, (result as Result.Success).data)
        verify(repository).insertTask(any())
    }

    @Test
    fun `enforces 1-level depth limit by rejecting subtask under another subtask`() = runBlocking {
        val childTask = Task(id = 20, title = "Child Subtask", parentTaskId = 10L)
        whenever(repository.getTaskById(20)).thenReturn(Result.Success(childTask))

        val result = useCase(Task(title = "Sub-subtask", parentTaskId = 20))

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is IllegalStateException)
        verify(repository, never()).insertTask(any())
    }

    @Test
    fun `rejects subtask when parent task not found`() = runBlocking {
        whenever(repository.getTaskById(999)).thenReturn(Result.Success(null))

        val result = useCase(Task(title = "Subtask", parentTaskId = 999))

        assertTrue(result is Result.Error)
        verify(repository, never()).insertTask(any())
    }

    @Test
    fun `propagates parent lookup error`() = runBlocking {
        val failure = RuntimeException("db gone")
        whenever(repository.getTaskById(10)).thenReturn(Result.Error(failure))

        val result = useCase(Task(title = "Subtask", parentTaskId = 10))

        assertTrue(result is Result.Error)
        assertEquals(failure, (result as Result.Error).exception)
        verify(repository, never()).insertTask(any())
    }
}
