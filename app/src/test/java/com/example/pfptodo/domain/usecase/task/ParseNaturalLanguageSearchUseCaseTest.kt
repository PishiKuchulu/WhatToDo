package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.SearchChipType
import com.example.pfptodo.domain.model.TaskFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ParseNaturalLanguageSearchUseCaseTest {

    private lateinit var useCase: ParseNaturalLanguageSearchUseCase
    private val zone = ZoneId.of("Asia/Tehran")
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-19T06:30:00Z"), zone) // Wednesday

    @Before
    fun setUp() {
        useCase = ParseNaturalLanguageSearchUseCase(fixedClock)
    }

    // ============================ Persian Tests ============================

    @Test
    fun `persian - single token priority high`() {
        val result1 = useCase("مهم")
        assertEquals(setOf(Priority.HIGH), result1.filter.priorities)
        assertEquals("", result1.keyword)
        assertEquals(1, result1.chips.size)
        assertEquals(SearchChipType.PRIORITY, result1.chips[0].type)

        val result2 = useCase("کارهای با اولویت بالا")
        assertEquals(setOf(Priority.HIGH), result2.filter.priorities)
        assertEquals("", result2.keyword)

        val result3 = useCase("فوری")
        assertEquals(setOf(Priority.HIGH), result3.filter.priorities)
    }

    @Test
    fun `persian - single token priority low`() {
        val result = useCase("کارهای با اولویت پایین")
        assertEquals(setOf(Priority.LOW), result.filter.priorities)
        assertEquals("", result.keyword)
    }

    @Test
    fun `persian - overdue status`() {
        val result1 = useCase("کارهای عقب افتاده")
        assertEquals(TaskFilter.Status.OVERDUE, result1.filter.status)
        assertEquals("", result1.keyword)

        val result2 = useCase("عقب‌افتاده")
        assertEquals(TaskFilter.Status.OVERDUE, result2.filter.status)

        val result3 = useCase("کارهای موعد گذشته")
        assertEquals(TaskFilter.Status.OVERDUE, result3.filter.status)
    }

    @Test
    fun `persian - completed status`() {
        val result1 = useCase("کارهای انجام شده")
        assertEquals(TaskFilter.Status.COMPLETED, result1.filter.status)

        val result2 = useCase("تکمیل‌شده")
        assertEquals(TaskFilter.Status.COMPLETED, result2.filter.status)

        val result3 = useCase("کارهای تمام شده")
        assertEquals(TaskFilter.Status.COMPLETED, result3.filter.status)
    }

    @Test
    fun `persian - incomplete status`() {
        val result1 = useCase("کارهای انجام نشده")
        assertEquals(TaskFilter.Status.ACTIVE, result1.filter.status)

        val result2 = useCase("انجام‌نشده")
        assertEquals(TaskFilter.Status.ACTIVE, result2.filter.status)

        val result3 = useCase("کارهای ناتمام")
        assertEquals(TaskFilter.Status.ACTIVE, result3.filter.status)
    }

    @Test
    fun `persian - date filters`() {
        val today = useCase("کارهای امروز")
        assertEquals(TaskFilter.DateFilter.TODAY, today.filter.dateFilter)

        val tomorrow = useCase("کارهای فردا")
        assertEquals(TaskFilter.DateFilter.TOMORROW, tomorrow.filter.dateFilter)

        val thisWeek = useCase("کارهای این هفته")
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, thisWeek.filter.dateFilter)

        val weekJari = useCase("کارهای هفته جاری")
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, weekJari.filter.dateFilter)

        val thisMonth = useCase("کارهای این ماه")
        assertEquals(TaskFilter.DateFilter.THIS_MONTH, thisMonth.filter.dateFilter)

        val monthJari = useCase("کارهای ماه جاری")
        assertEquals(TaskFilter.DateFilter.THIS_MONTH, monthJari.filter.dateFilter)
    }

    @Test
    fun `persian - recurring filter`() {
        val result1 = useCase("کارهای تکرارشونده")
        assertTrue(result1.filter.isRecurringOnly)

        val result2 = useCase("کارهای تکراری")
        assertTrue(result2.filter.isRecurringOnly)

        val result3 = useCase("کارهای دوره‌ای")
        assertTrue(result3.filter.isRecurringOnly)
    }

    @Test
    fun `persian - container phrase and keyword extraction`() {
        val result1 = useCase("کارهای مربوط به سفر")
        assertEquals("سفر", result1.keyword)
        assertEquals(1, result1.chips.size)
        assertEquals("سفر", result1.chips[0].customLabel)
        assertEquals(SearchChipType.KEYWORD, result1.chips[0].type)

        val result2 = useCase("کارهای خرید خانه")
        assertEquals("خرید خانه", result2.keyword)
    }

    @Test
    fun `persian - combined intent queries`() {
        val result1 = useCase("کارهای مهم این هفته")
        assertEquals(setOf(Priority.HIGH), result1.filter.priorities)
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, result1.filter.dateFilter)
        assertEquals("", result1.keyword)
        assertEquals(2, result1.chips.size)

        val result2 = useCase("کارهای انجام نشده امروز")
        assertEquals(TaskFilter.Status.ACTIVE, result2.filter.status)
        assertEquals(TaskFilter.DateFilter.TODAY, result2.filter.dateFilter)
        assertEquals("", result2.keyword)

        val result3 = useCase("کارهای مهم فردا")
        assertEquals(setOf(Priority.HIGH), result3.filter.priorities)
        assertEquals(TaskFilter.DateFilter.TOMORROW, result3.filter.dateFilter)

        val result4 = useCase("کارهای عقب افتاده سفر")
        assertEquals(TaskFilter.Status.OVERDUE, result4.filter.status)
        assertEquals("سفر", result4.keyword)
        assertEquals(2, result4.chips.size)
    }

    // ============================ English Tests ============================

    @Test
    fun `english - priority filters`() {
        val result1 = useCase("important tasks")
        assertEquals(setOf(Priority.HIGH), result1.filter.priorities)
        assertEquals("", result1.keyword)

        val result2 = useCase("high priority tasks")
        assertEquals(setOf(Priority.HIGH), result2.filter.priorities)

        val result3 = useCase("urgent tasks")
        assertEquals(setOf(Priority.HIGH), result3.filter.priorities)

        val result4 = useCase("low priority tasks")
        assertEquals(setOf(Priority.LOW), result4.filter.priorities)
    }

    @Test
    fun `english - status filters`() {
        val result1 = useCase("overdue tasks")
        assertEquals(TaskFilter.Status.OVERDUE, result1.filter.status)

        val result2 = useCase("completed tasks")
        assertEquals(TaskFilter.Status.COMPLETED, result2.filter.status)

        val result3 = useCase("done tasks")
        assertEquals(TaskFilter.Status.COMPLETED, result3.filter.status)

        val result4 = useCase("incomplete tasks")
        assertEquals(TaskFilter.Status.ACTIVE, result4.filter.status)

        val result5 = useCase("unfinished tasks")
        assertEquals(TaskFilter.Status.ACTIVE, result5.filter.status)
    }

    @Test
    fun `english - date filters`() {
        val result1 = useCase("today's tasks")
        assertEquals(TaskFilter.DateFilter.TODAY, result1.filter.dateFilter)

        val result2 = useCase("tomorrow's tasks")
        assertEquals(TaskFilter.DateFilter.TOMORROW, result2.filter.dateFilter)

        val result3 = useCase("tasks this week")
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, result3.filter.dateFilter)

        val result4 = useCase("current week tasks")
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, result4.filter.dateFilter)

        val result5 = useCase("tasks this month")
        assertEquals(TaskFilter.DateFilter.THIS_MONTH, result5.filter.dateFilter)
    }

    @Test
    fun `english - recurring filter`() {
        val result1 = useCase("recurring tasks")
        assertTrue(result1.filter.isRecurringOnly)

        val result2 = useCase("repeating tasks")
        assertTrue(result2.filter.isRecurringOnly)
    }

    @Test
    fun `english - keyword extraction`() {
        val result1 = useCase("travel tasks")
        assertEquals("travel", result1.keyword)

        val result2 = useCase("tasks related to budget")
        assertEquals("budget", result2.keyword)
    }

    @Test
    fun `english - combined intent queries`() {
        val result1 = useCase("important tasks this week")
        assertEquals(setOf(Priority.HIGH), result1.filter.priorities)
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, result1.filter.dateFilter)
        assertEquals("", result1.keyword)

        val result2 = useCase("incomplete tasks this week")
        assertEquals(TaskFilter.Status.ACTIVE, result2.filter.status)
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, result2.filter.dateFilter)

        val result3 = useCase("important tasks tomorrow")
        assertEquals(setOf(Priority.HIGH), result3.filter.priorities)
        assertEquals(TaskFilter.DateFilter.TOMORROW, result3.filter.dateFilter)

        val result4 = useCase("high priority overdue tasks")
        assertEquals(setOf(Priority.HIGH), result4.filter.priorities)
        assertEquals(TaskFilter.Status.OVERDUE, result4.filter.status)

        val result5 = useCase("important incomplete tasks this week")
        assertEquals(setOf(Priority.HIGH), result5.filter.priorities)
        assertEquals(TaskFilter.Status.ACTIVE, result5.filter.status)
        assertEquals(TaskFilter.DateFilter.THIS_WEEK, result5.filter.dateFilter)
    }

    // ============================ Edge Cases ============================

    @Test
    fun `empty and blank queries return default empty intent`() {
        val empty = useCase("")
        assertEquals("", empty.keyword)
        assertEquals(TaskFilter(), empty.filter)
        assertTrue(empty.chips.isEmpty())

        val whitespace = useCase("   \n\t  ")
        assertEquals("", whitespace.keyword)
        assertEquals(TaskFilter(), whitespace.filter)
        assertTrue(whitespace.chips.isEmpty())
    }

    @Test
    fun `punctuation only query returns empty keyword and default filter`() {
        val result = useCase("???!!!")
        assertEquals("", result.keyword)
        assertEquals(TaskFilter(), result.filter)
    }

    @Test
    fun `unsupported natural language query behaves as free text keyword`() {
        val result = useCase("Doctor Appointment with Dr Smith")
        assertEquals("Doctor Appointment with Dr Smith", result.keyword)
        assertEquals(TaskFilter(), result.filter)
    }

    @Test
    fun `no false positive for Persian words containing substrings of triggers`() {
        // "مهمان" contains "مهم" but is a different word
        val result = useCase("ناهار با مهمان")
        assertEquals(emptySet<Priority>(), result.filter.priorities)
        assertEquals("ناهار با مهمان", result.keyword)
    }
}
