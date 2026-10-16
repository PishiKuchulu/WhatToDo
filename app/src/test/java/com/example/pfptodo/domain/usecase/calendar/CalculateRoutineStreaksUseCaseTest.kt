package com.example.pfptodo.domain.usecase.calendar

import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CalculateRoutineStreaksUseCaseTest {

    private lateinit var useCase: CalculateRoutineStreaksUseCase
    private val zone = ZoneId.of("UTC")
    private val fixedInstant = Instant.parse("2026-08-19T10:00:00Z") // Wednesday
    private val clock = Clock.fixed(fixedInstant, zone)

    @Before
    fun setUp() {
        useCase = CalculateRoutineStreaksUseCase(clock)
    }

    @Test
    fun `no recurring tasks returns empty summary`() {
        val nonRecurring = listOf(
            Task(id = 1, title = "Task 1", recurrenceRule = RecurrenceRule.None),
            Task(id = 2, title = "Task 2", recurrenceRule = RecurrenceRule.None)
        )
        val summary = useCase(nonRecurring)
        assertEquals(0, summary.totalActiveRoutines)
        assertEquals(0, summary.overallBestStreak)
        assertEquals(0, summary.overallCurrentStreak)
        assertTrue(summary.routines.isEmpty())
    }

    @Test
    fun `recurring task with no completions returns 0 streaks`() {
        val routineTask = Task(
            id = 1,
            title = "Morning Workout",
            recurrenceRule = RecurrenceRule.Daily,
            isCompleted = false
        )
        val summary = useCase(listOf(routineTask))
        assertEquals(1, summary.totalActiveRoutines)
        val routine = summary.routines[0]
        assertEquals("Morning Workout", routine.title)
        assertEquals(0, routine.currentStreak)
        assertEquals(0, routine.bestStreak)
        assertEquals(0, routine.totalCompleted)
        assertFalse(routine.isCompletedForCurrentPeriod)
    }

    @Test
    fun `daily routine with consecutive completions ending today calculates active streak`() {
        // Completed Aug 17, Aug 18, and Aug 19 (today)
        val c1 = Task(id = 1, title = "Meditation", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-17T07:00:00Z"))
        val c2 = Task(id = 2, title = "Meditation", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-18T07:00:00Z"))
        val c3 = Task(id = 3, title = "Meditation", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-19T07:00:00Z"))
        val active = Task(id = 4, title = "Meditation", recurrenceRule = RecurrenceRule.Daily, isCompleted = false)

        val summary = useCase(listOf(c1, c2, c3, active))
        assertEquals(1, summary.totalActiveRoutines)
        val routine = summary.routines[0]
        assertEquals(3, routine.currentStreak)
        assertEquals(3, routine.bestStreak)
        assertEquals(3, routine.totalCompleted)
        assertTrue(routine.isCompletedForCurrentPeriod)
    }

    @Test
    fun `daily routine completed yesterday but not yet today maintains current streak`() {
        // Completed Aug 17, Aug 18 (yesterday). Today Aug 19 is pending.
        val c1 = Task(id = 1, title = "Reading", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-17T07:00:00Z"))
        val c2 = Task(id = 2, title = "Reading", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-18T07:00:00Z"))
        val active = Task(id = 3, title = "Reading", recurrenceRule = RecurrenceRule.Daily, isCompleted = false)

        val summary = useCase(listOf(c1, c2, active))
        val routine = summary.routines[0]
        assertEquals(2, routine.currentStreak)
        assertEquals(2, routine.bestStreak)
        assertFalse(routine.isCompletedForCurrentPeriod)
    }

    @Test
    fun `broken daily streak sets current streak to 0 while preserving best streak`() {
        // Completed Aug 10, 11, 12, 13 (4 days streak), then gap, completed Aug 16 (missed Aug 17, 18, 19)
        val c1 = Task(id = 1, title = "Guitar", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-10T07:00:00Z"))
        val c2 = Task(id = 2, title = "Guitar", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-11T07:00:00Z"))
        val c3 = Task(id = 3, title = "Guitar", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-12T07:00:00Z"))
        val c4 = Task(id = 4, title = "Guitar", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-13T07:00:00Z"))
        val c5 = Task(id = 5, title = "Guitar", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-16T07:00:00Z"))

        val summary = useCase(listOf(c1, c2, c3, c4, c5))
        val routine = summary.routines[0]
        assertEquals(0, routine.currentStreak)
        assertEquals(4, routine.bestStreak)
        assertEquals(5, routine.totalCompleted)
    }

    @Test
    fun `weekly routine streak calculation`() {
        // Completed Aug 5 (2 weeks ago), Aug 12 (1 week ago), Aug 19 (today)
        val c1 = Task(id = 1, title = "Weekly Review", recurrenceRule = RecurrenceRule.Weekly, isCompleted = true, completedAt = Instant.parse("2026-08-05T07:00:00Z"))
        val c2 = Task(id = 2, title = "Weekly Review", recurrenceRule = RecurrenceRule.Weekly, isCompleted = true, completedAt = Instant.parse("2026-08-12T07:00:00Z"))
        val c3 = Task(id = 3, title = "Weekly Review", recurrenceRule = RecurrenceRule.Weekly, isCompleted = true, completedAt = Instant.parse("2026-08-19T07:00:00Z"))

        val summary = useCase(listOf(c1, c2, c3))
        val routine = summary.routines[0]
        assertEquals(3, routine.currentStreak)
        assertEquals(3, routine.bestStreak)
        assertTrue(routine.isCompletedForCurrentPeriod)
    }

    @Test
    fun `multiple routine series aggregate overall best and current streaks`() {
        val r1c1 = Task(id = 1, title = "Habit A", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-19T07:00:00Z"))
        val r2c1 = Task(id = 2, title = "Habit B", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-18T07:00:00Z"))
        val r2c2 = Task(id = 3, title = "Habit B", recurrenceRule = RecurrenceRule.Daily, isCompleted = true, completedAt = Instant.parse("2026-08-19T07:00:00Z"))

        val summary = useCase(listOf(r1c1, r2c1, r2c2))
        assertEquals(2, summary.totalActiveRoutines)
        assertEquals(2, summary.overallBestStreak)
        assertEquals(2, summary.overallCurrentStreak)
    }
}
