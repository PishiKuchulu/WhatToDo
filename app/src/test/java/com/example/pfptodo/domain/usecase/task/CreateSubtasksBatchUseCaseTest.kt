package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CreateSubtasksBatchUseCaseTest {

    private lateinit var useCase: CreateSubtasksBatchUseCase
    private val repository: TaskRepository = mock()

    @Before
    fun setup() {
        useCase = CreateSubtasksBatchUseCase(repository)
    }

    @Test
    fun `batch creates multiple subtasks successfully`() = runBlocking {
        val parentTask = Task(id = 10, title = "Main Parent", parentTaskId = null)
        whenever(repository.getTaskById(10)).thenReturn(Result.Success(parentTask))
        whenever(repository.getSubtasksForTask(10)).thenReturn(flowOf(emptyList()))
        whenever(repository.insertTask(any())).thenReturn(Result.Success(101L), Result.Success(102L), Result.Success(103L))

        val result = useCase(parentId = 10, titles = listOf("Item 1", "Item 2", "Item 3"))
        assertTrue(result is Result.Success)
        assertEquals(listOf(101L, 102L, 103L), (result as Result.Success).data)
    }

    @Test
    fun `empty titles returns empty list immediately`() = runBlocking {
        val result = useCase(parentId = 10, titles = emptyList())
        assertTrue(result is Result.Success)
        assertEquals(emptyList<Long>(), (result as Result.Success).data)
    }

    @Test
    fun `rejects batch subtasks when parent is already a subtask`() = runBlocking {
        val childTask = Task(id = 20, title = "Child", parentTaskId = 5L)
        whenever(repository.getTaskById(20)).thenReturn(Result.Success(childTask))

        val result = useCase(parentId = 20, titles = listOf("Item 1"))
        assertTrue(result is Result.Error)
    }
}
