package com.example.pfptodo.domain.util

import com.example.pfptodo.domain.model.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Anchor-based recurrence engine.
 *
 * The anchor is the ORIGINAL occurrence date of a repeating series. Each next
 * occurrence is computed FROM THE ANCHOR, never from the previously completed
 * occurrence — this prevents the classic drift where Jan 31 → Feb 28 would
 * otherwise lead to Mar 28, Apr 28 …
 *
 * If no anchor is provided (legacy data), the supplied `from` date is used as
 * the anchor so behavior degrades gracefully.
 */
object RecurrenceEngine {

    /**
     * @param anchor   the original date of the repeating series (never moves)
     * @param from     the last completed occurrence; next() must be strictly after it
     * @param rule     the recurrence rule
     * @return the next occurrence, or null for [RecurrenceRule.None] or when no
     *         valid date can be derived.
     */
    fun calculateNextOccurrence(anchor: Instant, from: Instant, rule: RecurrenceRule): Instant? {
        if (rule is RecurrenceRule.None) return null

        val zone = ZoneId.systemDefault()
        val anchorDate = anchor.atZone(zone).toLocalDate()
        val anchorTime = anchor.atZone(zone).toLocalTime()
        val fromInstant = from.atZone(zone)

        // Guard: month-end clamping must never produce a date <= from.
        var candidate = nextOccurrenceDate(anchorDate, fromInstant.toLocalDate(), rule)
        var guard = 0
        while (candidate != null && !candidate.atTime(anchorTime).isAfter(fromInstant.toLocalDateTime())) {
            guard++
            if (guard > 1200) return null // safety valve (~3+ years of daily ticks)
            candidate = nextOccurrenceDate(candidate, fromInstant.toLocalDate(), rule)
        }
        return candidate?.let { date ->
            date.atTime(anchorTime).atZone(zone).toInstant()
        }
    }

    private fun nextOccurrenceDate(anchor: LocalDate, from: LocalDate, rule: RecurrenceRule): LocalDate? {
        var candidate = when (rule) {
            RecurrenceRule.Daily -> from.plusDays(1)
            RecurrenceRule.Weekly -> from.plusWeeks(1)
            RecurrenceRule.Monthly -> addMonthsClamped(anchor, monthsBetween(anchor, from) + 1)
            RecurrenceRule.Yearly -> addYearsClamped(anchor, yearsBetween(anchor, from) + 1)
            is RecurrenceRule.Interval -> from.plusDays(rule.days.toLong())
            RecurrenceRule.None -> return null
        }
        return candidate
    }

    private fun monthsBetween(anchor: LocalDate, from: LocalDate): Int =
        (from.year - anchor.year) * 12 + (from.monthValue - anchor.monthValue)

    private fun yearsBetween(anchor: LocalDate, from: LocalDate): Int = from.year - anchor.year

    /**
     * Adds [months] whole months to [anchor], clamping the day to the last day
     * of the target month (Jan 31 → Feb 28/29 → Mar 31).
     */
    private fun addMonthsClamped(anchor: LocalDate, months: Int): LocalDate {
        val targetYear = anchor.year + (anchor.monthValue - 1 + months) / 12
        val targetMonth = ((anchor.monthValue - 1 + months) % 12) + 1
        val lastDay = LocalDate.of(targetYear, targetMonth, 1).with(TemporalAdjusters.lastDayOfMonth())
        return lastDay.withDayOfMonth(minOf(anchor.dayOfMonth, lastDay.dayOfMonth))
    }

    /**
     * Adds [years] whole years, preserving end-of-month for Feb 29 anchors
     * (Feb 29 → Feb 28 in non-leap years).
     */
    private fun addYearsClamped(anchor: LocalDate, years: Int): LocalDate {
        val target = anchor.plusYears(years.toLong())
        return if (anchor.dayOfMonth == 29 && anchor.monthValue == 2 && target.dayOfMonth == 28) {
            // Anchor Feb 29 in a non-leap target year — clamp to Feb 28.
            target.withDayOfMonth(28)
        } else {
            target
        }
    }
}