package com.example.pfptodo.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * M-20: year-less birthdays must keep their month/day stable through the
 * Jalali conversions the editor performs — notably Feb 29 <-> 10 Esfand,
 * which used to clamp to Feb 28 whenever the reference year was not a leap year.
 */
class BirthdayDateConverterTest {

    @Test
    fun `feb 29 without a year converts to 10 esfand instead of clamping`() {
        // 2026 is not a leap year; the old code clamped against it.
        val fields = BirthdayDateConverter.gregorianToJalali(2, 29, null, referenceYear = 2026)
        assertEquals(BirthdayDateConverter.Fields(12, 10, null), fields)
    }

    @Test
    fun `10 esfand without a year converts back to feb 29`() {
        val fields = BirthdayDateConverter.jalaliToGregorian(12, 10, null, referenceJalaliYear = 1405)
        assertEquals(BirthdayDateConverter.Fields(2, 29, null), fields)
    }

    @Test
    fun `yearless mid-year date round trips gregorian to jalali and back`() {
        val jalali = BirthdayDateConverter.gregorianToJalali(6, 15, null, referenceYear = 2026)
        assertEquals(BirthdayDateConverter.Fields(3, 26, null), jalali)
        val back = BirthdayDateConverter.jalaliToGregorian(3, 26, null, referenceJalaliYear = 1405)
        assertEquals(BirthdayDateConverter.Fields(6, 15, null), back)
    }

    @Test
    fun `yearless picker seed for 10 esfand is a feb 29`() {
        // Seeds the Jalali date picker: an untouched confirm must return Feb 29.
        val seed = BirthdayDateConverter.jalaliYearlessToGregorian(12, 10, referenceJalaliYear = 1405)
        assertEquals(2, seed.monthValue)
        assertEquals(29, seed.dayOfMonth)
    }

    @Test
    fun `known year conversion stays exact`() {
        val fields = BirthdayDateConverter.gregorianToJalali(2, 29, 2024, referenceYear = 2026)
        assertEquals(BirthdayDateConverter.Fields(12, 10, 1402), fields)
        val back = BirthdayDateConverter.jalaliToGregorian(12, 10, 1402, referenceJalaliYear = 1405)
        assertEquals(BirthdayDateConverter.Fields(2, 29, 2024), back)
    }

    @Test
    fun `known year farvardin date round trips exactly`() {
        val jalali = BirthdayDateConverter.gregorianToJalali(3, 20, 2024, referenceYear = 2030)
        assertEquals(BirthdayDateConverter.Fields(1, 1, 1403), jalali)
        val back = BirthdayDateConverter.jalaliToGregorian(1, 1, 1403, referenceJalaliYear = 1405)
        assertEquals(BirthdayDateConverter.Fields(3, 20, 2024), back)
        assertEquals(LocalDate.of(2024, 3, 20), LocalDate.of(back.year!!, back.month, back.day))
    }
}
