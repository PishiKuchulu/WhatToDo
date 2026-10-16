package com.example.pfptodo.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class AppDateFormatTest {
    private val fa = Locale("fa")

    @Test
    fun `jalali month navigation round trips across year boundaries`() {
        val esfand = JalaliCalendarConverter.jalaliToGregorian(1404, 12, 1)
        val next = AppDateFormat.plusMonths(esfand, 1, CalendarType.JALALI)
        assertEquals(JalaliCalendarConverter.jalaliToGregorian(1405, 1, 1), next)
        assertEquals(esfand, AppDateFormat.plusMonths(next, -1, CalendarType.JALALI))
    }

    @Test
    fun `jalali month lengths follow the calendar`() {
        assertEquals(31, AppDateFormat.lengthOfMonth(JalaliCalendarConverter.jalaliToGregorian(1404, 1, 1), CalendarType.JALALI))
        assertEquals(30, AppDateFormat.lengthOfMonth(JalaliCalendarConverter.jalaliToGregorian(1404, 7, 1), CalendarType.JALALI))
        assertEquals(29, AppDateFormat.lengthOfMonth(JalaliCalendarConverter.jalaliToGregorian(1404, 12, 1), CalendarType.JALALI))
    }

    @Test
    fun `nowruz converts both ways`() {
        val date = LocalDate.of(2026, 3, 21)
        assertEquals(1, AppDateFormat.month(date, CalendarType.JALALI))
        assertEquals(1, AppDateFormat.dayOfMonth(date, CalendarType.JALALI))
        assertEquals(1405, AppDateFormat.year(date, CalendarType.JALALI))
        assertEquals(date, AppDateFormat.firstOfMonth(date.plusDays(5), CalendarType.JALALI))
    }

    @Test
    fun `persian locale gets persian digits and month names`() {
        assertEquals("فروردین ۱۴۰۵", AppDateFormat.monthTitle(LocalDate.of(2026, 3, 21), CalendarType.JALALI, fa))
        assertEquals("Farvardin 1405", AppDateFormat.monthTitle(LocalDate.of(2026, 3, 21), CalendarType.JALALI, Locale.US))
    }

    @Test
    fun `default calendar follows language`() {
        assertEquals(CalendarType.JALALI, CalendarType.defaultFor(fa))
        assertEquals(CalendarType.GREGORIAN, CalendarType.defaultFor(Locale.US))
    }

    @Test
    fun `birthday with known year converts exactly and back`() {
        val j = BirthdayDateConverter.gregorianToJalali(3, 21, 1990, 2026)
        assertEquals(1, j.month); assertEquals(1, j.day); assertEquals(1369, j.year)
        val g = BirthdayDateConverter.jalaliToGregorian(j.month, j.day, j.year, 1405)
        assertEquals(3, g.month); assertEquals(21, g.day); assertEquals(1990, g.year)
    }

    @Test
    fun `birthday without year keeps month and day through a Jalali round trip`() {
        val j = BirthdayDateConverter.gregorianToJalali(7, 15, null, 2026)
        val g = BirthdayDateConverter.jalaliToGregorian(j.month, j.day, null, 1405)
        assertEquals(7, g.month); assertEquals(15, g.day)
    }
}
