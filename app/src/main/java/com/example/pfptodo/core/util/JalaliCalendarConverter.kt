package com.example.pfptodo.core.util

import java.time.LocalDate

/**
 * Bidirectional converter between the Solar Hijri (Jalali / Persian) calendar and the
 * Gregorian calendar ([LocalDate]).
 *
 * Uses the leap-year "breaks" table approach (as in the widely used jalaali algorithm), which
 * matches the official calendar. The older 2820-year-cycle formula disagreed with it for some
 * years (e.g. it treated 1404 as a leap year), producing off-by-one days.
 * Zero external dependencies.
 */
object JalaliCalendarConverter {

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Int, b: Int): Int = a - b * (a / b)

    private fun jalCal(jy: Int): JalCal {
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1

        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG

        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    /** Checks if a given Jalali year is a leap year (366 days). */
    fun isJalaliLeapYear(year: Int): Boolean = jalCal(year).leap == 0

    /** Converts a Jalali date (year, month 1..12, day 1..31) to a Gregorian [LocalDate]. */
    fun jalaliToGregorian(jYear: Int, jMonth: Int, jDay: Int): LocalDate {
        require(jMonth in 1..12) { "Jalali month must be in 1..12, but was $jMonth" }
        val maxDays = when {
            jMonth <= 6 -> 31
            jMonth <= 11 -> 30
            isJalaliLeapYear(jYear) -> 30
            else -> 29
        }
        val day = jDay.coerceIn(1, maxDays)
        val r = jalCal(jYear)
        val firstOfFarvardin = LocalDate.of(r.gy, 3, r.march)
        val offset = (jMonth - 1) * 31 - div(jMonth, 7) * (jMonth - 7) + day - 1
        return firstOfFarvardin.plusDays(offset.toLong())
    }

    /** Converts a Gregorian [LocalDate] to a [JalaliDate]. */
    fun gregorianToJalali(date: LocalDate): JalaliDate {
        var jy = date.year - 621
        val r = jalCal(jy)
        val firstOfFarvardin = LocalDate.of(date.year, 3, r.march)
        var k = (date.toEpochDay() - firstOfFarvardin.toEpochDay()).toInt()
        if (k >= 0) {
            if (k <= 185) {
                return JalaliDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }
}
