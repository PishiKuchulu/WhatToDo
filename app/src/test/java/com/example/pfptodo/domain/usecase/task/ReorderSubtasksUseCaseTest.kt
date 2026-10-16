package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReorderSubtasksUseCaseTest {

    private lateinit var useCase: ReorderSubtasksUseCase
    private val repository: TaskRepository = mock()

    @Before
    fun setup() {
        useCase = ReorderSubtasksUseCase(repository)
    }

    @Test
    fun testReordersSubtasksSequentially() {
        runBlocking {
            whenever(repository.updateTaskOrder(any(), any())).thenReturn(Result.Success(Unit))

            val result = useCase(parentId = 10, orderedSubtaskIds = listOf(103L, 101L, 102L))
            assertTrue(result is Result.Success)

            verify(repository).updateTaskOrder(eq(103L), eq(0))
            verify(repository).updateTaskOrder(eq(101L), eq(1))
            verify(repository).updateTaskOrder(eq(102L), eq(2))
        }
    }

    @Test
    fun testReturnsErrorAndStopsWhenUpdateTaskOrderFails() {
        runBlocking {
            val expectedError = RuntimeException("Order update failed")
            whenever(repository.updateTaskOrder(any(), any())).thenReturn(Result.Error(expectedError))

            val result = useCase(parentId = 10, orderedSubtaskIds = listOf(103L, 101L, 102L))

            assertTrue(result is Result.Error)
            assertEquals(expectedError, (result as Result.Error).exception)
            // Fails fast on the first update: only one repository call, no partial reorder.
            verify(repository, times(1)).updateTaskOrder(any(), any())
            verify(repository).updateTaskOrder(eq(103L), eq(0))
        }
    }
}
