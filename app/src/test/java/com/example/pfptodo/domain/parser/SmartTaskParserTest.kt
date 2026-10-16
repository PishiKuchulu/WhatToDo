package com.example.pfptodo.domain.parser

import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class SmartTaskParserTest {

    private lateinit var parser: LocalTaskParser
    private lateinit var fixedClock: Clock
    private val fixedZone = ZoneId.of("Asia/Tehran")
    // Base date: Wednesday, August 19, 2026 at 10:00 AM
    private val baseInstant = Instant.parse("2026-08-19T06:30:00Z") // 10:00 AM Tehran time (UTC+3:30)

    @Before
    fun setUp() {
        parser = LocalTaskParser()
        fixedClock = Clock.fixed(baseInstant, fixedZone)
    }

    // ============================ Persian Tests ============================

    @Test
    fun parse_persian_doctorMeetingTomorrow5pmHighPriority() {
        val input = "جلسه دکتر فردا ساعت ۵ با اولویت بالا"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("جلسه دکتر", task.title)
        assertEquals(LocalDate.of(2026, 8, 20), task.date) // tomorrow
        assertEquals(LocalTime.of(17, 0), task.time)
        assertEquals(Priority.HIGH, task.priority)
        assertTrue(result.confidence > 0.7f)
    }

    @Test
    fun parse_persian_buyMilkSaturday8amWithTag() {
        val input = "خرید شیر شنبه ساعت ۸ صبح #خرید"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("خرید شیر", task.title)
        // Next Saturday after Wednesday Aug 19 is Saturday Aug 22
        assertEquals(LocalDate.of(2026, 8, 22), task.date)
        assertEquals(LocalTime.of(8, 0), task.time)
        assertEquals(listOf("خرید"), task.tags)
    }

    @Test
    fun parse_persian_billPaymentJalaliDateUrgent() {
        // 1405/02/20 corresponds to 2026-05-10
        val input = "پرداخت قبض ۱۴۰۵/۰۲/۲۰ فوری"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("پرداخت قبض", task.title)
        assertEquals(LocalDate.of(2026, 5, 10), task.date)
        assertEquals(Priority.HIGH, task.priority)
    }

    @Test
    fun parse_persian_callAliDayAfterTomorrow9pm() {
        val input = "تماس با علی پس فردا ساعت ۹ شب"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("تماس با علی", task.title)
        // Day after tomorrow: Friday Aug 21
        assertEquals(LocalDate.of(2026, 8, 21), task.date)
        assertEquals(LocalTime.of(21, 0), task.time)
    }

    @Test
    fun parse_persian_dailyExerciseDaily6am() {
        val input = "ورزش روزانه هر روز ساعت ۶ صبح"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("ورزش روزانه", task.title)
        assertEquals(RecurrenceRule.Daily, task.recurrenceRule)
        assertEquals(LocalTime.of(6, 0), task.time)
    }

    @Test
    fun parse_persian_halfHourTime() {
        val input = "کلاس زبان فردا ساعت ۵ و نیم عصر"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("کلاس زبان", task.title)
        assertEquals(LocalDate.of(2026, 8, 20), task.date)
        assertEquals(LocalTime.of(17, 30), task.time)
    }

    // ============================ English Tests ============================

    @Test
    fun parse_english_buyMilkTomorrowWithTag() {
        val input = "Buy milk tomorrow #Shopping"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("Buy milk", task.title)
        assertEquals(LocalDate.of(2026, 8, 20), task.date)
        assertEquals(listOf("Shopping"), task.tags)
    }

    @Test
    fun parse_english_finishReportFriday9amUrgent() {
        val input = "Finish report Friday at 9 AM urgent"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("Finish report", task.title)
        // Next Friday after Wednesday Aug 19 is Friday Aug 21
        assertEquals(LocalDate.of(2026, 8, 21), task.date)
        assertEquals(LocalTime.of(9, 0), task.time)
        assertEquals(Priority.HIGH, task.priority)
    }

    @Test
    fun parse_english_teamSyncEveryMonday10amWithTag() {
        val input = "Team sync every Monday at 10:00 #Work"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("Team sync", task.title)
        assertEquals(RecurrenceRule.Weekly, task.recurrenceRule)
        assertEquals(LocalTime.of(10, 0), task.time)
        assertEquals(listOf("Work"), task.tags)
    }

    @Test
    fun parse_english_isoDateLowPriority() {
        val input = "Dentist appointment 2026-09-15 low priority"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("Dentist appointment", task.title)
        assertEquals(LocalDate.of(2026, 9, 15), task.date)
        assertEquals(Priority.LOW, task.priority)
    }

    @Test
    fun parse_persian_zwnj_dayAfterTomorrowAndWeekday() {
        // ZWNJ in پس‌فردا (\u200C)
        val input1 = "جلسه دکتر پس\u200Cفردا ساعت ۵ عصر"
        val result1 = parser.parse(input1, fixedClock)
        assertEquals("جلسه دکتر", result1.parsedTask.title)
        assertEquals(LocalDate.of(2026, 8, 21), result1.parsedTask.date)
        assertEquals(LocalTime.of(17, 0), result1.parsedTask.time)

        // ZWNJ in سه‌شنبه (\u200C)
        val input2 = "کلاس زبان سه\u200Cشنبه ساعت ۹ صبح"
        val result2 = parser.parse(input2, fixedClock)
        assertEquals("کلاس زبان", result2.parsedTask.title)
        assertEquals(LocalDate.of(2026, 8, 25), result2.parsedTask.date) // Next Tuesday
        assertEquals(LocalTime.of(9, 0), result2.parsedTask.time)
    }

    @Test
    fun parse_persian_punctuationTrimming() {
        val inputDash = "جلسه کاری - فردا ساعت ۱۰"
        val resultDash = parser.parse(inputDash, fixedClock)
        assertEquals("جلسه کاری", resultDash.parsedTask.title)
        assertEquals(LocalDate.of(2026, 8, 20), resultDash.parsedTask.date)
        assertEquals(LocalTime.of(10, 0), resultDash.parsedTask.time)

        val inputComma = "خرید میوه، فردا ساعت ۴"
        val resultComma = parser.parse(inputComma, fixedClock)
        assertEquals("خرید میوه", resultComma.parsedTask.title)
        assertEquals(LocalDate.of(2026, 8, 20), resultComma.parsedTask.date)
        assertEquals(LocalTime.of(16, 0), resultComma.parsedTask.time)
    }

    @Test
    fun parse_persian_compoundRelativeWords() {
        val input = "دیدار با دوستان فردا شب"
        val result = parser.parse(input, fixedClock)
        assertEquals("دیدار با دوستان", result.parsedTask.title)
        assertEquals(LocalDate.of(2026, 8, 20), result.parsedTask.date)
        assertEquals(LocalTime.of(20, 0), result.parsedTask.time)
    }

    @Test
    fun parse_english_punctuationTrimming() {
        val inputDash = "Doctor appointment - tomorrow at 5 PM"
        val resultDash = parser.parse(inputDash, fixedClock)
        assertEquals("Doctor appointment", resultDash.parsedTask.title)
        assertEquals(LocalDate.of(2026, 8, 20), resultDash.parsedTask.date)
        assertEquals(LocalTime.of(17, 0), resultDash.parsedTask.time)

        val inputColon = "Team retrospective: Friday at 3 PM"
        val resultColon = parser.parse(inputColon, fixedClock)
        assertEquals("Team retrospective", resultColon.parsedTask.title)
        assertEquals(LocalDate.of(2026, 8, 21), resultColon.parsedTask.date)
        assertEquals(LocalTime.of(15, 0), resultColon.parsedTask.time)
    }

    @Test
    fun parse_plainText_returnsBasicTitleWithBaseConfidence() {
        val input = "Clean the kitchen"
        val result = parser.parse(input, fixedClock)

        val task = result.parsedTask
        assertEquals("Clean the kitchen", task.title)
        assertNull(task.date)
        assertNull(task.time)
        assertEquals(Priority.MEDIUM, task.priority)
        assertEquals(0.5f, result.confidence, 0.01f)
    }

    @Test
    fun parse_emptyInput_returnsEmptyResultWithZeroConfidence() {
        val result = parser.parse("   ", fixedClock)
        assertEquals("", result.parsedTask.title)
        assertEquals(0.0f, result.confidence, 0.01f)
    }

    @Test
    fun parse_english_multilineChecklist_extractsSubtasks() {
        val input = """
            Grocery shopping tomorrow at 5pm #errands
            - Milk
            - Eggs
            * Bread
            1. Butter
        """.trimIndent()

        val result = parser.parse(input, fixedClock)
        val task = result.parsedTask

        assertEquals("Grocery shopping", task.title)
        assertEquals(LocalDate.of(2026, 8, 20), task.date)
        assertEquals(LocalTime.of(17, 0), task.time)
        assertEquals(listOf("errands"), task.tags)
        assertEquals(listOf("Milk", "Eggs", "Bread", "Butter"), task.subtaskTitles)
    }

    @Test
    fun parse_persian_multilineChecklist_extractsSubtasks() {
        val input = """
            خرید سوپرمارکت فردا ساعت ۵ عصر #خرید
            - شیر کم چرب
            * نان بربری
            • تخم مرغ
        """.trimIndent()

        val result = parser.parse(input, fixedClock)
        val task = result.parsedTask

        assertEquals("خرید سوپرمارکت", task.title)
        assertEquals(LocalDate.of(2026, 8, 20), task.date)
        assertEquals(LocalTime.of(17, 0), task.time)
        assertEquals(listOf("خرید"), task.tags)
        assertEquals(listOf("شیر کم چرب", "نان بربری", "تخم مرغ"), task.subtaskTitles)
    }
}
