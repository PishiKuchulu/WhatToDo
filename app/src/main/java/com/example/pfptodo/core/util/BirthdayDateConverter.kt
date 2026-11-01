package com.example.pfptodo.core.util

import java.time.LocalDate
import java.time.Month
import java.time.Year

/**
 * Birthdays are stored as a Gregorian month/day (+ optional year). These helpers let the editor
 * show and accept them in the Jalali calendar. With a known year the conversion is exact; without
 * one, the date is placed in a leap-anchored year (see [leapAnchorYear]) so the month/day
 * round-trips unchanged — in particular Feb 29 converts to 10 Esfand and back, instead of being
 * clamped to Feb 28 whenever the reference year is not a leap year. (Clamping Feb 29 for a
 * non-leap OCCURRENCE year happens only in the occurrence projection, `Birthday.occurrenceIn`,
 * never in what the editor saves.)
 */
object BirthdayDateConverter {

    data class Fields(val month: Int, val day: Int, val year: Int?)

    fun gregorianToJalali(month: Int, day: Int, birthYear: Int?, referenceYear: Int): Fields {
        val year = birthYear ?: leapAnchorYear(referenceYear)
        val safeMonth = month.coerceIn(1, 12)
        val safeDay = day.coerceIn(1, Month.of(safeMonth).length(Year.isLeap(year.toLong())))
        val j = JalaliCalendarConverter.gregorianToJalali(LocalDate.of(year, safeMonth, safeDay))
        return Fields(j.month, j.day, if (birthYear != null) j.year else null)
    }

    fun jalaliToGregorian(month: Int, day: Int, jalaliYear: Int?, referenceJalaliYear: Int): Fields {
        val safeMonth = month.coerceIn(1, 12)
        val placedYear = jalaliYear ?: jalaliAnchorYear(safeMonth, referenceJalaliYear)
        val g = JalaliCalendarConverter.jalaliToGregorian(placedYear, safeMonth, day.coerceIn(1, 31))
        return Fields(g.monthValue, g.dayOfMonth, if (jalaliYear != null) g.year else null)
    }

    /**
     * Concrete Gregorian date for a year-less Jalali month/day, placed in the leap anchor
     * year. Used to seed a date picker so that confirming it untouched returns the same
     * month/day the editor already shows.
     */
    fun jalaliYearlessToGregorian(month: Int, day: Int, referenceJalaliYear: Int): LocalDate {
        val safeMonth = month.coerceIn(1, 12)
        return JalaliCalendarConverter.jalaliToGregorian(
            jalaliAnchorYear(safeMonth, referenceJalaliYear),
            safeMonth,
            day.coerceIn(1, 31)
        )
    }

    /** Most recent Gregorian leap year at or before [referenceYear]. */
    private fun leapAnchorYear(referenceYear: Int): Int {
        var year = referenceYear
        while (!Year.isLeap(year.toLong())) year--
        return year
    }

    /**
     * The Jalali year paired with the Gregorian leap-anchor year for [referenceJalaliYear]:
     * Jalali months 1..9 of year Y fall in Gregorian year Y + 621 and months 10..12 in
     * Y + 622, so the two halves anchor to the same leap year from opposite sides.
     * (Dey 1..10 sits on the seam between the two halves and can land one day off — an
     * unavoidable ambiguity for a year-less date. Feb 29 <-> 10 Esfand is exact.)
     */
    private fun jalaliAnchorYear(jalaliMonth: Int, referenceJalaliYear: Int): Int {
        val leapGregorian = leapAnchorYear(referenceJalaliYear + 622)
        return if (jalaliMonth <= 9) leapGregorian - 621 else leapGregorian - 622
    }
}
