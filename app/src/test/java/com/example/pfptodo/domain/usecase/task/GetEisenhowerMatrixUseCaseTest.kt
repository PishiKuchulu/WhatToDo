package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneId

class GetEisenhowerMatrixUseCaseTest {

    private lateinit var useCase: GetEisenhowerMatrixUseCase
    private val repository: TaskRepository = mock()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    @Before
    fun setup() {
        useCase = GetEisenhowerMatrixUseCase(repository)
    }

    @Test
    fun `partitions tasks into correct four quadrants`() = runBlocking {
        val q1Task = Task(
            id = 1,
            title = "Urgent and Important",
            priority = Priority.HIGH,
            dueDate = today.atTime(14, 0).atZone(zone).toInstant()
        )
        val q2Task = Task(
            id = 2,
            title = "Important but Not Urgent",
            priority = Priority.HIGH,
            dueDate = today.plusDays(5).atTime(10, 0).atZone(zone).toInstant()
        )
        val q3Task = Task(
            id = 3,
            title = "Urgent but Not Important",
            priority = Priority.MEDIUM,
            dueDate = today.atTime(18, 0).atZone(zone).toInstant()
        )
        val q4Task = Task(
            id = 4,
            title = "Neither Urgent nor Important",
            priority = Priority.LOW,
            dueDate = null
        )
        val completedTask = Task(
            id = 5,
            title = "Completed Task",
            priority = Priority.HIGH,
            isCompleted = true,
            dueDate = today.atTime(12, 0).atZone(zone).toInstant()
        )

        whenever(repository.getAllMainTasks()).thenReturn(
            flowOf(listOf(q1Task, q2Task, q3Task, q4Task, completedTask))
        )

        val matrix = useCase().first()

        assertEquals(1, matrix.q1DoFirst.size)
        assertEquals(1L, matrix.q1DoFirst[0].id)

        assertEquals(1, matrix.q2Schedule.size)
        assertEquals(2L, matrix.q2Schedule[0].id)

        assertEquals(1, matrix.q3Delegate.size)
        assertEquals(3L, matrix.q3Delegate[0].id)

        assertEquals(1, matrix.q4Eliminate.size)
        assertEquals(4L, matrix.q4Eliminate[0].id)

        assertEquals(4, matrix.totalTasks)
    }

    @Test
    fun `overdue high priority task falls into Q1`() = runBlocking {
        val overdueHigh = Task(
            id = 10,
            title = "Overdue High Priority",
            priority = Priority.HIGH,
            dueDate = today.minusDays(2).atTime(12, 0).atZone(zone).toInstant()
        )
        whenever(repository.getAllMainTasks()).thenReturn(flowOf(listOf(overdueHigh)))

        val matrix = useCase().first()
        assertEquals(1, matrix.q1DoFirst.size)
        assertEquals(10L, matrix.q1DoFirst[0].id)
    }
}
