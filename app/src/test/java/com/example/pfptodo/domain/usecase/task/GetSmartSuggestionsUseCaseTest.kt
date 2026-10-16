package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.SuggestionAction
import com.example.pfptodo.domain.model.SuggestionType
import com.example.pfptodo.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class GetSmartSuggestionsUseCaseTest {

    private lateinit var useCase: GetSmartSuggestionsUseCase
    private val zone = ZoneId.of("UTC")
    // Reference instant: 2026-08-20T12:00:00Z
    private val baseInstant = Instant.parse("2026-08-20T12:00:00Z")
    private val fixedClock = Clock.fixed(baseInstant, zone)

    @Before
    fun setUp() {
        useCase = GetSmartSuggestionsUseCase()
    }

    @Test
    fun `no tasks returns empty suggestions`() {
        val result = useCase(emptyList(), clock = fixedClock)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `only completed tasks returns empty suggestions`() {
        val tasks = listOf(
            Task(id = 1, title = "Task 1", isCompleted = true, completedAt = baseInstant),
            Task(id = 2, title = "Task 2", isCompleted = true, completedAt = baseInstant)
        )
        val result = useCase(tasks, clock = fixedClock)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `subtasks are excluded from root suggestions evaluation`() {
        val tasks = listOf(
            Task(id = 1, title = "Subtask", parentTaskId = 99L, priority = Priority.HIGH, dueDate = Instant.parse("2026-08-10T10:00:00Z"))
        )
        val result = useCase(tasks, clock = fixedClock)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `overdue high priority task triggers OVERDUE_HIGH_PRIORITY`() {
        val task = Task(
            id = 10,
            title = "Crucial Server Fix",
            priority = Priority.HIGH,
            dueDate = Instant.parse("2026-08-18T10:00:00Z")
        )
        val result = useCase(listOf(task), clock = fixedClock)

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(SuggestionType.OVERDUE_HIGH_PRIORITY, suggestion.type)
        assertEquals(100, suggestion.rank)
        assertEquals(10L, suggestion.relatedTaskId)
        assertTrue(suggestion.action is SuggestionAction.OpenTask)
        assertEquals(10L, (suggestion.action as SuggestionAction.OpenTask).taskId)
        assertEquals(listOf("Crucial Server Fix"), suggestion.formatArgs)
    }

    @Test
    fun `multiple overdue tasks trigger OVERDUE_CLEANUP suggestion`() {
        val tasks = listOf(
            Task(id = 1, title = "Task A", priority = Priority.MEDIUM, dueDate = Instant.parse("2026-08-15T10:00:00Z")),
            Task(id = 2, title = "Task B", priority = Priority.LOW, dueDate = Instant.parse("2026-08-16T10:00:00Z"))
        )
        val result = useCase(tasks, clock = fixedClock)

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(SuggestionType.OVERDUE_CLEANUP, suggestion.type)
        assertEquals(90, suggestion.rank)
        assertTrue(suggestion.action is SuggestionAction.FilterOverdue)
        assertEquals(listOf("2"), suggestion.formatArgs)
    }

    @Test
    fun `high priority unscheduled task triggers HIGH_PRIORITY_UNSCHEDULED`() {
        val task = Task(
            id = 20,
            title = "Draft Contract",
            priority = Priority.HIGH,
            dueDate = null,
            createdAt = Instant.parse("2026-08-15T10:00:00Z")
        )
        val result = useCase(listOf(task), clock = fixedClock)

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(SuggestionType.HIGH_PRIORITY_UNSCHEDULED, suggestion.type)
        assertEquals(80, suggestion.rank)
        assertEquals(20L, suggestion.relatedTaskId)
        assertTrue(suggestion.action is SuggestionAction.OpenTaskEditor)
    }

    @Test
    fun `stale task untouched for more than 14 days triggers STALE_TASK`() {
        val task = Task(
            id = 30,
            title = "Old Backlog Idea",
            priority = Priority.LOW,
            dueDate = null,
            createdAt = Instant.parse("2026-07-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-01T10:00:00Z")
        )
        val result = useCase(listOf(task), clock = fixedClock)

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(SuggestionType.STALE_TASK, suggestion.type)
        assertEquals(70, suggestion.rank)
        assertEquals(30L, suggestion.relatedTaskId)
    }

    @Test
    fun `overdue recurring task triggers RECURRING_TASK_REVIEW`() {
        val task = Task(
            id = 40,
            title = "Weekly Team Report",
            priority = Priority.MEDIUM,
            recurrenceRule = RecurrenceRule.Weekly,
            dueDate = Instant.parse("2026-08-10T10:00:00Z")
        )
        val result = useCase(listOf(task), clock = fixedClock)

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(SuggestionType.RECURRING_TASK_REVIEW, suggestion.type)
        assertEquals(60, suggestion.rank)
        assertEquals(40L, suggestion.relatedTaskId)
    }

    @Test
    fun `task with partial subtasks completed triggers CHECKLIST_PROGRESS`() {
        val task = Task(
            id = 50,
            title = "Trip Packing",
            priority = Priority.MEDIUM,
            subtaskCount = 5,
            completedSubtaskCount = 3
        )
        val result = useCase(listOf(task), clock = fixedClock)

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(SuggestionType.CHECKLIST_PROGRESS, suggestion.type)
        assertEquals(50, suggestion.rank)
        assertEquals(50L, suggestion.relatedTaskId)
        assertEquals(listOf("Trip Packing", "3", "5"), suggestion.formatArgs)
    }

    @Test
    fun `checklist with 0 completed subtasks does not trigger CHECKLIST_PROGRESS`() {
        val task = Task(
            id = 51,
            title = "Unstarted Checklist",
            subtaskCount = 4,
            completedSubtaskCount = 0
        )
        val result = useCase(listOf(task), clock = fixedClock)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `checklist with all subtasks completed does not trigger CHECKLIST_PROGRESS`() {
        val task = Task(
            id = 52,
            title = "All Subtasks Done",
            subtaskCount = 4,
            completedSubtaskCount = 4
        )
        val result = useCase(listOf(task), clock = fixedClock)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `three or more active tasks due today trigger DAILY_TRIAGE`() {
        val tasks = listOf(
            Task(id = 1, title = "Task 1", dueDate = Instant.parse("2026-08-20T09:00:00Z")),
            Task(id = 2, title = "Task 2", dueDate = Instant.parse("2026-08-20T14:00:00Z")),
            Task(id = 3, title = "Task 3", dueDate = Instant.parse("2026-08-20T18:00:00Z"))
        )
        val result = useCase(tasks, clock = fixedClock)

        assertEquals(1, result.size)
        val suggestion = result[0]
        assertEquals(SuggestionType.DAILY_TRIAGE, suggestion.type)
        assertEquals(40, suggestion.rank)
        assertTrue(suggestion.action is SuggestionAction.FilterToday)
        assertEquals(listOf("3"), suggestion.formatArgs)
    }

    @Test
    fun `less than three active tasks due today do not trigger DAILY_TRIAGE`() {
        val tasks = listOf(
            Task(id = 1, title = "Task 1", dueDate = Instant.parse("2026-08-20T09:00:00Z")),
            Task(id = 2, title = "Task 2", dueDate = Instant.parse("2026-08-20T14:00:00Z"))
        )
        val result = useCase(tasks, clock = fixedClock)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deterministic ranking and maximum 3 visible suggestions`() {
        val tasks = listOf(
            // Rank 100: Overdue High
            Task(id = 1, title = "Overdue High", priority = Priority.HIGH, dueDate = Instant.parse("2026-08-10T10:00:00Z")),
            // Rank 90: Overdue cleanup (since we will have 2 overdue tasks: #1 and #2)
            Task(id = 2, title = "Overdue Med", priority = Priority.MEDIUM, dueDate = Instant.parse("2026-08-12T10:00:00Z")),
            // Rank 80: Unscheduled High
            Task(id = 3, title = "Unscheduled High", priority = Priority.HIGH, dueDate = null),
            // Rank 70: Stale
            Task(id = 4, title = "Stale", priority = Priority.LOW, dueDate = null, createdAt = Instant.parse("2026-07-01T00:00:00Z"), updatedAt = Instant.parse("2026-07-01T00:00:00Z")),
            // Rank 50: Checklist
            Task(id = 5, title = "Checklist", subtaskCount = 4, completedSubtaskCount = 2),
            // Rank 40: Daily Triage (tasks due today: #6, #7, #8)
            Task(id = 6, title = "Today 1", dueDate = Instant.parse("2026-08-20T10:00:00Z")),
            Task(id = 7, title = "Today 2", dueDate = Instant.parse("2026-08-20T11:00:00Z")),
            Task(id = 8, title = "Today 3", dueDate = Instant.parse("2026-08-20T12:00:00Z"))
        )

        val result = useCase(tasks, clock = fixedClock)

        // Capped at 3
        assertEquals(3, result.size)
        // Ranks in strict descending order
        assertEquals(SuggestionType.OVERDUE_HIGH_PRIORITY, result[0].type)
        assertEquals(100, result[0].rank)

        assertEquals(SuggestionType.OVERDUE_CLEANUP, result[1].type)
        assertEquals(90, result[1].rank)

        assertEquals(SuggestionType.HIGH_PRIORITY_UNSCHEDULED, result[2].type)
        assertEquals(80, result[2].rank)
    }

    @Test
    fun `dismissed suggestions are omitted from output`() {
        val tasks = listOf(
            Task(id = 1, title = "Overdue High", priority = Priority.HIGH, dueDate = Instant.parse("2026-08-10T10:00:00Z")),
            Task(id = 2, title = "Unscheduled High", priority = Priority.HIGH, dueDate = null)
        )

        // Dismiss the overdue high suggestion
        val dismissed = setOf("overdue_high_1")
        val result = useCase(tasks, dismissedIds = dismissed, clock = fixedClock)

        assertEquals(1, result.size)
        assertEquals(SuggestionType.HIGH_PRIORITY_UNSCHEDULED, result[0].type)
        assertEquals(2L, result[0].relatedTaskId)
    }
}
