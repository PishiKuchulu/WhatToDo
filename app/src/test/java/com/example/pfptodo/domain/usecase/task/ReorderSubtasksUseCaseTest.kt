package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReorderSubtasksUseCaseTest {

    private lateinit var useCase: ReorderSubtasksUseCase
    private val repository: TaskRepository = mock()

    private class RecordingTransactionRunner : TransactionRunner {
        var invocations = 0
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            invocations++
            return block()
        }
    }

    @Before
    fun setup() {
        useCase = ReorderSubtasksUseCase(repository)
        whenever(repository.getSubtasksForTask(10)).thenReturn(
            flowOf(
                listOf(
                    Task(id = 101, title = "A", parentTaskId = 10),
                    Task(id = 102, title = "B", parentTaskId = 10),
                    Task(id = 103, title = "C", parentTaskId = 10)
                )
            )
        )
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

    @Test
    fun testRejectsIdsThatDoNotBelongToParent() {
        runBlocking {
            whenever(repository.updateTaskOrder(any(), any())).thenReturn(Result.Success(Unit))

            // 999 is not a subtask of parent 10 — the whole reorder must be refused
            // before any row is renumbered.
            val result = useCase(parentId = 10, orderedSubtaskIds = listOf(101L, 999L, 102L))

            assertTrue(result is Result.Error)
            verify(repository, never()).updateTaskOrder(any(), any())
        }
    }

    @Test
    fun testRejectsDuplicateIds() {
        runBlocking {
            val result = useCase(parentId = 10, orderedSubtaskIds = listOf(101L, 101L))

            assertTrue(result is Result.Error)
            verify(repository, never()).updateTaskOrder(any(), any())
        }
    }

    @Test
    fun testRunsUpdatesInsideTransactionWhenRunnerIsWired() {
        runBlocking {
            val runner = RecordingTransactionRunner()
            val transactionalUseCase = ReorderSubtasksUseCase(repository, runner)
            whenever(repository.updateTaskOrder(any(), any())).thenReturn(Result.Success(Unit))

            val result = transactionalUseCase(parentId = 10, orderedSubtaskIds = listOf(102L, 101L))

            assertTrue(result is Result.Success)
            assertEquals(1, runner.invocations)
            verify(repository).updateTaskOrder(eq(102L), eq(0))
            verify(repository).updateTaskOrder(eq(101L), eq(1))
        }
    }
}
