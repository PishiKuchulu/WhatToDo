package com.example.pfptodo.domain.usecase.calendar

import com.example.pfptodo.domain.model.DayLoadDensity
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class GetSevenDayPlanningUseCaseTest {

    private lateinit var useCase: GetSevenDayPlanningUseCase
    private val zone = ZoneId.of("UTC")
    private val fixedInstant = Instant.parse("2026-08-19T10:00:00Z") // Wednesday, 1395-05-28 (1405-05-28)
    private val clock = Clock.fixed(fixedInstant, zone)
    private val today = LocalDate.now(clock)

    @Before
    fun setUp() {
        val densityUseCase = CalculateDailyLoadDensityUseCase(clock)
        useCase = GetSevenDayPlanningUseCase(densityUseCase, clock)
    }

    @Test
    fun `planning strip produces exactly 7 consecutive days starting today`() {
        val items = useCase(emptyList(), selectedDate = today, locale = Locale.ENGLISH)
        assertEquals(7, items.size)
        assertEquals(today, items[0].date)
        assertEquals(today.plusDays(1), items[1].date)
        assertEquals(today.plusDays(6), items[6].date)
        assertTrue(items[0].isToday)
        assertFalse(items[1].isToday)
    }

    @Test
    fun `selected date flag reflects selected date parameter`() {
        val selected = today.plusDays(3)
        val items = useCase(emptyList(), selectedDate = selected, locale = Locale.ENGLISH)
        assertFalse(items[0].isSelected)
        assertTrue(items[3].isSelected)
        assertEquals(selected, items[3].date)
    }

    @Test
    fun `persian locale formats jalali day numbers and names correctly`() {
        val items = useCase(emptyList(), selectedDate = today, locale = Locale("fa", "IR"))
        assertEquals(7, items.size)
        // 2026-08-19 is Wednesday (۴شنبه) in Persian Solar calendar (Mordad 28, 1405)
        assertEquals("۴شنبه", items[0].dayOfWeekName)
        assertEquals("28", items[0].displayDayNumber)
        assertEquals("مرداد", items[0].displayMonthName)
    }

    @Test
    fun `gregorian locale formats gregorian day numbers and names correctly`() {
        val items = useCase(emptyList(), selectedDate = today, locale = Locale.US)
        assertEquals(7, items.size)
        assertEquals("Wed", items[0].dayOfWeekName)
        assertEquals("19", items[0].displayDayNumber)
        assertEquals("Aug", items[0].displayMonthName)
    }

    @Test
    fun `tasks on days populate taskCount, highPriorityCount and loadDensity accurately`() {
        val todayTask = Task(id = 1, title = "T1", dueDate = Instant.parse("2026-08-19T11:00:00Z"))
        val day2Task1 = Task(id = 2, title = "T2", dueDate = Instant.parse("2026-08-20T12:00:00Z"), priority = Priority.HIGH)
        val day2Task2 = Task(id = 3, title = "T3", dueDate = Instant.parse("2026-08-20T15:00:00Z"))
        val day2Task3 = Task(id = 4, title = "T4", dueDate = Instant.parse("2026-08-20T18:00:00Z"))
        val overdueTask = Task(id = 5, title = "Overdue", dueDate = Instant.parse("2026-08-10T10:00:00Z"), isCompleted = false)

        val items = useCase(listOf(todayTask, day2Task1, day2Task2, day2Task3, overdueTask), selectedDate = today, locale = Locale.ENGLISH)

        // Today item
        assertEquals(1, items[0].taskCount)
        assertEquals(1, items[0].overdueCount)
        assertEquals(DayLoadDensity.LOW, items[0].loadDensity)

        // Day + 1 item (3 tasks, 1 high priority -> composite score 4 -> MEDIUM)
        assertEquals(3, items[1].taskCount)
        assertEquals(1, items[1].highPriorityCount)
        assertEquals(0, items[1].overdueCount)
        assertEquals(DayLoadDensity.MEDIUM, items[1].loadDensity)

        // Empty Day + 2 item
        assertEquals(0, items[2].taskCount)
        assertEquals(DayLoadDensity.NONE, items[2].loadDensity)
    }
}
