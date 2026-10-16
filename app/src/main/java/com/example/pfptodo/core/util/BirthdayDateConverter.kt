package com.example.pfptodo.core.util

import java.time.LocalDate

/**
 * Birthdays are stored as a Gregorian month/day (+ optional year). These helpers let the editor
 * show and accept them in the Jalali calendar. With a known year the conversion is exact; without
 * one, [referenceYear] (the current Gregorian year) is used to place the date.
 */
object BirthdayDateConverter {

    data class Fields(val month: Int, val day: Int, val year: Int?)

    fun gregorianToJalali(month: Int, day: Int, birthYear: Int?, referenceYear: Int): Fields {
        val year = birthYear ?: referenceYear
        val safeDay = day.coerceIn(1, java.time.Month.of(month.coerceIn(1, 12)).length(java.time.Year.isLeap(year.toLong())))
        val j = JalaliCalendarConverter.gregorianToJalali(LocalDate.of(year, month.coerceIn(1, 12), safeDay))
        return Fields(j.month, j.day, if (birthYear != null) j.year else null)
    }

    fun jalaliToGregorian(month: Int, day: Int, jalaliYear: Int?, referenceJalaliYear: Int): Fields {
        val g = JalaliCalendarConverter.jalaliToGregorian(jalaliYear ?: referenceJalaliYear, month.coerceIn(1, 12), day.coerceIn(1, 31))
        return Fields(g.monthValue, g.dayOfMonth, if (jalaliYear != null) g.year else null)
    }
}
