package com.example.pfptodo.domain.usecase.calendar

import com.example.pfptodo.domain.model.DayLoadDensity
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalculateDailyLoadDensityUseCaseTest {

    private lateinit var useCase: CalculateDailyLoadDensityUseCase
    private val zone = ZoneId.of("UTC")
    private val fixedInstant = Instant.parse("2026-08-19T10:00:00Z") // Wednesday
    private val clock = Clock.fixed(fixedInstant, zone)
    private val today = LocalDate.now(clock)

    @Before
    fun setUp() {
        useCase = CalculateDailyLoadDensityUseCase(clock)
    }

    @Test
    fun `empty task list returns NONE density`() {
        val density = useCase(today, emptyList())
        assertEquals(DayLoadDensity.NONE, density)
    }

    @Test
    fun `1 to 2 normal tasks returns LOW density`() {
        val task1 = Task(id = 1, title = "Task 1", dueDate = Instant.parse("2026-08-19T11:00:00Z"))
        val task2 = Task(id = 2, title = "Task 2", dueDate = Instant.parse("2026-08-19T14:00:00Z"))

        val density1 = useCase(today, listOf(task1))
        assertEquals(DayLoadDensity.LOW, density1)

        val density2 = useCase(today, listOf(task1, task2))
        assertEquals(DayLoadDensity.LOW, density2)
    }

    @Test
    fun `3 to 4 normal tasks returns MEDIUM density`() {
        val tasks = (1..3).map {
            Task(id = it.toLong(), title = "Task $it", dueDate = Instant.parse("2026-08-19T11:00:00Z"))
        }
        val density = useCase(today, tasks)
        assertEquals(DayLoadDensity.MEDIUM, density)
    }

    @Test
    fun `5 or more tasks returns HIGH density`() {
        val tasks = (1..5).map {
            Task(id = it.toLong(), title = "Task $it", dueDate = Instant.parse("2026-08-19T11:00:00Z"))
        }
        val density = useCase(today, tasks)
        assertEquals(DayLoadDensity.HIGH, density)
    }

    @Test
    fun `high priority uncompleted tasks increase load score`() {
        // 2 tasks: 1 normal, 1 high priority uncompleted => composite score = 2 scheduled + 1 high priority = 3 => MEDIUM
        val task1 = Task(id = 1, title = "Normal", priority = Priority.MEDIUM, dueDate = Instant.parse("2026-08-19T11:00:00Z"))
        val task2 = Task(id = 2, title = "High", priority = Priority.HIGH, isCompleted = false, dueDate = Instant.parse("2026-08-19T14:00:00Z"))

        val density = useCase(today, listOf(task1, task2))
        assertEquals(DayLoadDensity.MEDIUM, density)
    }

    @Test
    fun `active overdue tasks increase today load score`() {
        // 1 task today + 2 active overdue tasks from yesterday => composite score = 1 + 0 + 2 = 3 => MEDIUM
        val todayTask = Task(id = 1, title = "Today", dueDate = Instant.parse("2026-08-19T11:00:00Z"))
        val overdueTask1 = Task(id = 2, title = "Overdue 1", isCompleted = false, dueDate = Instant.parse("2026-08-18T10:00:00Z"))
        val overdueTask2 = Task(id = 3, title = "Overdue 2", isCompleted = false, dueDate = Instant.parse("2026-08-17T10:00:00Z"))

        val densityToday = useCase(today, listOf(todayTask, overdueTask1, overdueTask2))
        assertEquals(DayLoadDensity.MEDIUM, densityToday)

        // Overdue tasks do not increase load score on future dates
        val futureDate = today.plusDays(1)
        val densityFuture = useCase(futureDate, listOf(todayTask, overdueTask1, overdueTask2))
        assertEquals(DayLoadDensity.NONE, densityFuture)
    }

    @Test
    fun `completed tasks do not count towards high priority overdue penalty`() {
        val completedOverdue = Task(id = 1, title = "Done", isCompleted = true, dueDate = Instant.parse("2026-08-17T10:00:00Z"))
        val density = useCase(today, listOf(completedOverdue))
        assertEquals(DayLoadDensity.NONE, density)
    }
}
