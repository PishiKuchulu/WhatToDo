package com.example.pfptodo.domain.model

import java.time.LocalDate
import java.time.MonthDay

/**
 * A birthday or recurring annual anniversary for a person.
 *
 * Deliberately stores month/day separately from any year so the occurrence can be
 * projected onto any target year. [birthYear] is optional: when present the app can
 * show the age being turned; when absent only the date is shown.
 */
data class Birthday(
    val id: Long = 0,
    val name: String,
    val month: Int,
    val day: Int,
    val birthYear: Int? = null,
    val notes: String? = null,
    val reminderMinutesOfDay: Int = DEFAULT_REMINDER_MINUTES,
    val notifyOnDayBefore: Boolean = false,
    val contactId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Normalised month/day that handles Feb 29 for non-leap target years. */
    val monthDay: MonthDay
        get() = MonthDay.of(month, day)

    /**
     * The next occurrence on or after [from]. Feb 29 birthdays fall back to
     * Feb 28 in non-leap years rather than skipping the year entirely.
     */
    fun nextOccurrence(from: LocalDate): LocalDate {
        val candidate = occurrenceIn(from.year)
        if (!candidate.isBefore(from)) return candidate
        return occurrenceIn(from.year + 1)
    }

    /** The date this birthday falls on during [year], with leap-day fallback. */
    fun occurrenceIn(year: Int): LocalDate {
        val isLeap = java.time.Year.isLeap(year.toLong())
        val effectiveDay = if (month == 2 && day == 29 && !isLeap) 28 else day
        return LocalDate.of(year, month, effectiveDay)
    }

    /** Age turned on [occurrence], or null when the birth year is unknown. */
    fun ageAt(occurrence: LocalDate): Int? {
        val year = birthYear ?: return null
        return occurrence.year - year
    }

    /** Days from [from] until the next occurrence (0 means today). */
    fun daysUntilNext(from: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(from, nextOccurrence(from))

    companion object {
        const val DEFAULT_REMINDER_MINUTES = 540
        const val MIN_YEAR = 1900
    }
}
