package com.example.pfptodo.core.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JalaliCalendarConverterTest {

    @Test
    fun digitNormalizer_convertsPersianAndArabicDigits() {
        val persian = "۱۲۳۴۵۶۷۸۹۰"
        assertEquals("1234567890", DigitNormalizer.toAsciiDigits(persian))

        val arabic = "١٢٣٤٥٦٧٨٩٠"
        assertEquals("1234567890", DigitNormalizer.toAsciiDigits(arabic))

        val mixed = "ساعت ۵:۳۰ عصر با ١٠ نفر"
        assertEquals("ساعت 5:30 عصر با 10 نفر", DigitNormalizer.toAsciiDigits(mixed))
    }

    @Test
    fun jalaliToGregorian_knownDates() {
        // 1403/01/01 -> 2024-03-20 (Nowruz 1403)
        val g1 = JalaliCalendarConverter.jalaliToGregorian(1403, 1, 1)
        assertEquals(LocalDate.of(2024, 3, 20), g1)

        // 1405/02/20 -> 2026-05-10
        val g2 = JalaliCalendarConverter.jalaliToGregorian(1405, 2, 20)
        assertEquals(LocalDate.of(2026, 5, 10), g2)

        // 1400/01/01 -> 2021-03-21
        val g3 = JalaliCalendarConverter.jalaliToGregorian(1400, 1, 1)
        assertEquals(LocalDate.of(2021, 3, 21), g3)
    }

    @Test
    fun gregorianToJalali_roundTrip() {
        val dates = listOf(
            LocalDate.of(2024, 3, 20),
            LocalDate.of(2026, 5, 10),
            LocalDate.of(2026, 8, 19),
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2030, 12, 31)
        )

        for (date in dates) {
            val j = JalaliCalendarConverter.gregorianToJalali(date)
            val convertedBack = JalaliCalendarConverter.jalaliToGregorian(j.year, j.month, j.day)
            assertEquals("Round-trip failed for $date -> ($j)", date, convertedBack)
        }
    }

    @Test
    fun leapYear_check() {
        assertTrue(JalaliCalendarConverter.isJalaliLeapYear(1399))
        assertTrue(JalaliCalendarConverter.isJalaliLeapYear(1403))
        assertFalse(JalaliCalendarConverter.isJalaliLeapYear(1404))
        assertTrue(JalaliCalendarConverter.isJalaliLeapYear(1408))
        assertFalse(JalaliCalendarConverter.isJalaliLeapYear(1400))
        assertFalse(JalaliCalendarConverter.isJalaliLeapYear(1401))
    }

    @Test
    fun lastDayOfEsfand1404_isTheDayBeforeNowruz1405() {
        assertEquals(JalaliCalendarConverter.JalaliDate(1404, 12, 29), JalaliCalendarConverter.gregorianToJalali(LocalDate.of(2026, 3, 20)))
        assertEquals(JalaliCalendarConverter.JalaliDate(1405, 1, 1), JalaliCalendarConverter.gregorianToJalali(LocalDate.of(2026, 3, 21)))
    }

    @Test
    fun roundTrip_everyDayOverAWideRange() {
        var d = LocalDate.of(1990, 1, 1)
        while (d.isBefore(LocalDate.of(2060, 1, 1))) {
            val j = JalaliCalendarConverter.gregorianToJalali(d)
            assertEquals(d, JalaliCalendarConverter.jalaliToGregorian(j.year, j.month, j.day))
            d = d.plusDays(1)
        }
    }
}
