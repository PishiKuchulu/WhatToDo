package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetFilteredTasksUseCaseTest {

    private lateinit var useCase: GetFilteredTasksUseCase
    private val repository: TaskRepository = mock()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(ZoneId.systemDefault())
    private val startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
    private val startOfTomorrow = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
    private val startOfDayAfterTomorrow = today.plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant()

    @Before
    fun setup() {
        useCase = GetFilteredTasksUseCase(repository)
    }

    @Test
    fun `TODAY filter includes only tasks due today`() = runBlocking {
        val dueToday = Task(
            id = 11, title = "Today",
            dueDate = today.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        )
        val dueTomorrow = Task(
            id = 12, title = "Tomorrow",
            dueDate = today.plusDays(1).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        )
        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(dueToday, dueTomorrow)))

        val result = useCase("", TaskFilter(dateFilter = TaskFilter.DateFilter.TODAY), SortOrder.MANUAL).first()
        assertEquals(listOf(11L), result.map { it.id })
    }

    @Test
    fun `UPCOMING filter excludes today but includes tomorrow`() = runBlocking {
        val dueToday = Task(
            id = 11, title = "Today",
            dueDate = today.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        )
        val dueTomorrow = Task(
            id = 12, title = "Tomorrow",
            dueDate = today.plusDays(1).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        )
        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(dueToday, dueTomorrow)))

        val result = useCase("", TaskFilter(dateFilter = TaskFilter.DateFilter.UPCOMING), SortOrder.MANUAL).first()
        assertEquals(listOf(12L), result.map { it.id })
    }

    @Test
    fun `OVERDUE status counts only fully past tasks`() = runBlocking {
        val overdue = Task(
            id = 21, title = "Old",
            dueDate = today.minusDays(2).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant()
        )
        val dueTodayLate = Task(
            id = 22, title = "LaterToday",
            dueDate = today.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().plusSeconds(2 * 3600)
        )
        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(overdue, dueTodayLate)))

        val result = useCase("", TaskFilter(status = TaskFilter.Status.OVERDUE), SortOrder.MANUAL).first()
        assertEquals(listOf(21L), result.map { it.id })
    }

    @Test
    fun `TOMORROW filter includes only tomorrow`() = runBlocking {
        val dueToday = Task(
            id = 31, title = "Today",
            dueDate = today.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        )
        val dueTomorrow = Task(
            id = 32, title = "Tomorrow",
            dueDate = today.plusDays(1).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        )
        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(dueToday, dueTomorrow)))

        val result = useCase("", TaskFilter(dateFilter = TaskFilter.DateFilter.TOMORROW), SortOrder.MANUAL).first()
        assertEquals(listOf(32L), result.map { it.id })
    }

    @Test
    fun `TAG filter with single tag matches tasks with that tag`() = runBlocking {
        val tag1 = com.example.pfptodo.domain.model.Tag(id = 1L, name = "Work")
        val tag2 = com.example.pfptodo.domain.model.Tag(id = 2L, name = "Personal")

        val task1 = Task(id = 1, title = "Task 1", tags = listOf(tag1))
        val task2 = Task(id = 2, title = "Task 2", tags = listOf(tag2))
        val task3 = Task(id = 3, title = "Task 3", tags = listOf(tag1, tag2))

        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(task1, task2, task3)))

        val filter = TaskFilter(tagIds = setOf(1L))
        val result = useCase("", filter, SortOrder.MANUAL).first()
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun `TAG filter with OR mode matches tasks with any selected tag`() = runBlocking {
        val tag1 = com.example.pfptodo.domain.model.Tag(id = 1L, name = "Work")
        val tag2 = com.example.pfptodo.domain.model.Tag(id = 2L, name = "Personal")
        val tag3 = com.example.pfptodo.domain.model.Tag(id = 3L, name = "Urgent")

        val task1 = Task(id = 1, title = "Task 1", tags = listOf(tag1))
        val task2 = Task(id = 2, title = "Task 2", tags = listOf(tag2))
        val task3 = Task(id = 3, title = "Task 3", tags = listOf(tag3))

        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(task1, task2, task3)))

        val filter = TaskFilter(tagIds = setOf(1L, 2L), tagFilterMode = TaskFilter.TagFilterMode.OR)
        val result = useCase("", filter, SortOrder.MANUAL).first()
        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun `TAG filter with AND mode matches only tasks with all selected tags`() = runBlocking {
        val tag1 = com.example.pfptodo.domain.model.Tag(id = 1L, name = "Work")
        val tag2 = com.example.pfptodo.domain.model.Tag(id = 2L, name = "Urgent")

        val task1 = Task(id = 1, title = "Task 1", tags = listOf(tag1))
        val task2 = Task(id = 2, title = "Task 2", tags = listOf(tag2))
        val task3 = Task(id = 3, title = "Task 3", tags = listOf(tag1, tag2))

        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(task1, task2, task3)))

        val filter = TaskFilter(tagIds = setOf(1L, 2L), tagFilterMode = TaskFilter.TagFilterMode.AND)
        val result = useCase("", filter, SortOrder.MANUAL).first()
        assertEquals(listOf(3L), result.map { it.id })
    }

    @Test
    fun `isRecurringOnly filter matches only recurring tasks`() = runBlocking {
        val recurringTask = Task(id = 1, title = "Daily Standup", recurrenceRule = com.example.pfptodo.domain.model.RecurrenceRule.Daily)
        val nonRecurringTask = Task(id = 2, title = "One-off Task", recurrenceRule = com.example.pfptodo.domain.model.RecurrenceRule.None)

        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(recurringTask, nonRecurringTask)))

        val filter = TaskFilter(isRecurringOnly = true)
        val result = useCase("", filter, SortOrder.MANUAL).first()
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `THIS_WEEK filter includes tasks due within current week`() = runBlocking {
        val clock = java.time.Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneId.of("UTC")) // Wednesday
        val localUseCase = GetFilteredTasksUseCase(repository, clock)

        val taskMidWeek = Task(id = 1, title = "MidWeek", dueDate = Instant.parse("2026-08-20T10:00:00Z"))
        val taskNextWeek = Task(id = 2, title = "NextWeek", dueDate = Instant.parse("2026-08-30T10:00:00Z"))

        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(taskMidWeek, taskNextWeek)))

        val filter = TaskFilter(dateFilter = TaskFilter.DateFilter.THIS_WEEK)
        val result = localUseCase("", filter, SortOrder.MANUAL).first()
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `THIS_MONTH filter includes tasks due within current month`() = runBlocking {
        val clock = java.time.Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneId.of("UTC"))
        val localUseCase = GetFilteredTasksUseCase(repository, clock)

        val taskThisMonth = Task(id = 1, title = "This Month", dueDate = Instant.parse("2026-08-25T10:00:00Z"))
        val taskNextMonth = Task(id = 2, title = "Next Month", dueDate = Instant.parse("2026-09-10T10:00:00Z"))

        whenever(repository.getAllMainTasks()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(taskThisMonth, taskNextMonth)))

        val filter = TaskFilter(dateFilter = TaskFilter.DateFilter.THIS_MONTH)
        val result = localUseCase("", filter, SortOrder.MANUAL).first()
        assertEquals(listOf(1L), result.map { it.id })
    }
}