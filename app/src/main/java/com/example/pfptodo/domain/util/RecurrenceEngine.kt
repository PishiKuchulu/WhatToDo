package com.example.pfptodo.domain.util

import com.example.pfptodo.domain.model.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Anchor-based recurrence engine.
 *
 * The anchor is the ORIGINAL occurrence date of a repeating series. Every
 * occurrence lives on the anchor's grid (anchor + k periods); each next
 * occurrence is computed FROM THE ANCHOR, never from the previously completed
 * occurrence — this prevents the classic drift where Jan 31 → Feb 28 would
 * otherwise lead to Mar 28, Apr 28 …, and it means snoozing one occurrence
 * (which only moves that occurrence's due date) never shifts the series'
 * cadence.
 *
 * If the completed occurrence is finished late, whole periods are skipped
 * ahead until the next occurrence lies strictly in the future (skip-ahead
 * policy) — a daily task completed 10 days late resumes tomorrow, not with a
 * burst of already-past occurrences.
 *
 * If no anchor is provided (legacy data), the supplied `from` date is used as
 * the anchor so behavior degrades gracefully.
 */
object RecurrenceEngine {

    /**
     * @param anchor   the original date of the repeating series (never moves)
     * @param from     the last completed occurrence; next() must be strictly after it
     * @param rule     the recurrence rule
     * @param now      the current moment; next() must also be strictly after it,
     *                 so a late completion skips already-past grid slots
     * @return the next occurrence, or null for [RecurrenceRule.None] or when no
     *         valid date can be derived.
     */
    fun calculateNextOccurrence(
        anchor: Instant,
        from: Instant,
        rule: RecurrenceRule,
        now: Instant = Instant.now()
    ): Instant? {
        if (rule is RecurrenceRule.None) return null

        val zone = ZoneId.systemDefault()
        val anchorDate = anchor.atZone(zone).toLocalDate()
        val anchorTime = anchor.atZone(zone).toLocalTime()
        val fromDateTime = from.atZone(zone).toLocalDateTime()
        val nowDateTime = now.atZone(zone).toLocalDateTime()

        var candidate = nextOccurrenceDate(anchorDate, fromDateTime.toLocalDate(), rule) ?: return null
        var guard = 0
        // The first grid slot after `from` can still lie in the past when the
        // completion is late: advance by whole periods (staying on the anchor
        // grid, day-clamped for Monthly/Yearly) until the occurrence is
        // strictly after both `from` and `now`.
        while (!candidate.atTime(anchorTime).isAfter(fromDateTime) ||
            !candidate.atTime(anchorTime).isAfter(nowDateTime)
        ) {
            guard++
            if (guard > 1200) return null // safety valve (~3+ years of daily ticks)
            candidate = stepDate(candidate, anchorDate, rule) ?: return null
        }
        return candidate.atTime(anchorTime).atZone(zone).toInstant()
    }

    /**
     * The first occurrence strictly after [from] on the anchor's grid.
     * Daily/Weekly/Interval slots are anchor + k·period, so a snoozed [from]
     * (off-grid) resolves to the next grid slot, not `from` + one period.
     * Monthly/Yearly clamp the day via the anchor.
     */
    private fun nextOccurrenceDate(anchor: LocalDate, from: LocalDate, rule: RecurrenceRule): LocalDate? {
        return when (rule) {
            RecurrenceRule.Daily -> gridDate(anchor, from, 1L)
            RecurrenceRule.Weekly -> gridDate(anchor, from, 7L)
            RecurrenceRule.Monthly -> addMonthsClamped(anchor, monthsBetween(anchor, from) + 1)
            RecurrenceRule.Yearly -> addYearsClamped(anchor, yearsBetween(anchor, from) + 1)
            is RecurrenceRule.Interval -> gridDate(anchor, from, maxOf(1L, rule.days.toLong()))
            RecurrenceRule.None -> null
        }
    }

    /** Advances one whole period from an on-grid [candidate], staying on the anchor grid. */
    private fun stepDate(candidate: LocalDate, anchor: LocalDate, rule: RecurrenceRule): LocalDate? {
        return when (rule) {
            RecurrenceRule.Daily -> candidate.plusDays(1)
            RecurrenceRule.Weekly -> candidate.plusWeeks(1)
            RecurrenceRule.Monthly -> addMonthsClamped(anchor, monthsBetween(anchor, candidate) + 1)
            RecurrenceRule.Yearly -> addYearsClamped(anchor, yearsBetween(anchor, candidate) + 1)
            is RecurrenceRule.Interval -> candidate.plusDays(maxOf(1L, rule.days.toLong()))
            RecurrenceRule.None -> null
        }
    }

    /** Smallest anchor + k·[periodDays] (k ≥ 0 counting from the anchor) strictly after [from]. */
    private fun gridDate(anchor: LocalDate, from: LocalDate, periodDays: Long): LocalDate {
        val elapsed = ChronoUnit.DAYS.between(anchor, from)
        val steps = Math.floorDiv(elapsed, periodDays) + 1
        return anchor.plusDays(steps * periodDays)
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
