package com.example.pfptodo.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar-aware date helpers. Everything in the database is a Gregorian [LocalDate] / [Instant];
 * these functions only decide how a date is *shown* and how months are navigated, so switching the
 * calendar setting converts every task and birthday date automatically — in both directions.
 */
object AppDateFormat {

    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    private val latinMonths = arrayOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
    )

    fun year(date: LocalDate, type: CalendarType): Int =
        if (type == CalendarType.JALALI) JalaliCalendarConverter.gregorianToJalali(date).year else date.year

    fun month(date: LocalDate, type: CalendarType): Int =
        if (type == CalendarType.JALALI) JalaliCalendarConverter.gregorianToJalali(date).month else date.monthValue

    fun dayOfMonth(date: LocalDate, type: CalendarType): Int =
        if (type == CalendarType.JALALI) JalaliCalendarConverter.gregorianToJalali(date).day else date.dayOfMonth

    /** [month] is 1..12 in the given calendar. */
    fun monthName(month: Int, type: CalendarType, locale: Locale, short: Boolean = false): String {
        if (type == CalendarType.JALALI) {
            val index = (month - 1).coerceIn(0, 11)
            return if (locale.language.equals("fa", ignoreCase = true)) persianMonths[index] else latinMonths[index]
        }
        return java.time.Month.of(month.coerceIn(1, 12)).getDisplayName(
            if (short) java.time.format.TextStyle.SHORT else java.time.format.TextStyle.FULL, locale
        )
    }

    /** e.g. "Khordad 1405" / "خرداد ۱۴۰۵" / "June 2026". */
    fun monthTitle(date: LocalDate, type: CalendarType, locale: Locale): String =
        localizeDigits("${monthName(month(date, type), type, locale)} ${year(date, type)}", locale)

    /** e.g. "Jun 15, 2026" / "15 Khordad 1405". */
    fun date(date: LocalDate, type: CalendarType, locale: Locale): String =
        if (type == CalendarType.JALALI) {
            localizeDigits(
                "${dayOfMonth(date, type)} ${monthName(month(date, type), type, locale, short = true)} ${year(date, type)}",
                locale
            )
        } else {
            date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", locale))
        }

    /** Short form without the year: "Jun 15" / "15 Khordad". */
    fun dateShort(date: LocalDate, type: CalendarType, locale: Locale): String =
        if (type == CalendarType.JALALI) {
            localizeDigits("${dayOfMonth(date, type)} ${monthName(month(date, type), type, locale, short = true)}", locale)
        } else {
            date.format(DateTimeFormatter.ofPattern("MMM dd", locale))
        }

    fun dateTime(instant: Instant, zone: ZoneId, type: CalendarType, locale: Locale): String {
        val zoned = instant.atZone(zone)
        val time = String.format(Locale.US, "%02d:%02d", zoned.hour, zoned.minute)
        return date(zoned.toLocalDate(), type, locale) + " " + localizeDigits(time, locale)
    }

    fun firstOfMonth(date: LocalDate, type: CalendarType): LocalDate =
        if (type == CalendarType.JALALI) {
            val j = JalaliCalendarConverter.gregorianToJalali(date)
            JalaliCalendarConverter.jalaliToGregorian(j.year, j.month, 1)
        } else {
            date.withDayOfMonth(1)
        }

    /** [firstOfMonth] must already be the first day of a month in [type]. */
    fun plusMonths(firstOfMonth: LocalDate, months: Int, type: CalendarType): LocalDate =
        if (type == CalendarType.JALALI) {
            val j = JalaliCalendarConverter.gregorianToJalali(firstOfMonth)
            val index = j.year * 12 + (j.month - 1) + months
            JalaliCalendarConverter.jalaliToGregorian(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1, 1)
        } else {
            firstOfMonth.plusMonths(months.toLong())
        }

    fun lengthOfMonth(firstOfMonth: LocalDate, type: CalendarType): Int =
        if (type == CalendarType.JALALI) {
            val j = JalaliCalendarConverter.gregorianToJalali(firstOfMonth)
            when {
                j.month <= 6 -> 31
                j.month <= 11 -> 30
                JalaliCalendarConverter.isJalaliLeapYear(j.year) -> 30
                else -> 29
            }
        } else {
            firstOfMonth.lengthOfMonth()
        }

    /** First weekday of the grid: Saturday for the Jalali calendar, locale-defined otherwise. */
    fun firstDayOfWeek(type: CalendarType, locale: Locale): DayOfWeek =
        if (type == CalendarType.JALALI) DayOfWeek.SATURDAY
        else java.time.temporal.WeekFields.of(locale).firstDayOfWeek

    /** Number of blank cells before day 1 in a week grid starting on [firstDay]. */
    fun leadingBlanks(firstOfMonth: LocalDate, firstDay: DayOfWeek): Int =
        (firstOfMonth.dayOfWeek.value - firstDay.value + 7) % 7

    fun sameMonth(a: LocalDate, b: LocalDate, type: CalendarType): Boolean =
        year(a, type) == year(b, type) && month(a, type) == month(b, type)

    /** Persian users see Persian digits; other locales are left untouched. */
    fun localizeDigits(text: String, locale: Locale): String {
        if (!locale.language.equals("fa", ignoreCase = true)) return text
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(if (ch in '0'..'9') ('\u06F0' + (ch - '0')) else ch)
        return sb.toString()
    }

    fun number(value: Int, locale: Locale): String = localizeDigits(value.toString(), locale)
}
