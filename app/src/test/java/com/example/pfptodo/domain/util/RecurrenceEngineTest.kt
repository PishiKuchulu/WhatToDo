package com.example.pfptodo.domain.util

import com.example.pfptodo.domain.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecurrenceEngineTest {

    // ===== helpers =====

    private fun jan31(): Instant = Instant.parse("2024-01-31T10:00:00Z")
    private fun feb29_2024(): Instant = Instant.parse("2024-02-29T10:00:00Z")
    private fun feb28_2024(): Instant = Instant.parse("2024-02-28T10:00:00Z")
    private fun jan1(): Instant = Instant.parse("2024-01-01T10:00:00Z")

    // Existing scenarios complete each occurrence on time, so `now` is passed
    // as the completed occurrence itself; the late-completion tests below pass
    // an explicit later `now`.

    // ===== Daily / Weekly / Interval (anchor grid) =====

    @Test
    fun `calculateNextOccurrence Daily returns next day`() {
        val next = RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.Daily, now = jan1())
        assertEquals(Instant.parse("2024-01-02T10:00:00Z"), next)
    }

    @Test
    fun `calculateNextOccurrence Weekly returns next week`() {
        val next = RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.Weekly, now = jan1())
        assertEquals(Instant.parse("2024-01-08T10:00:00Z"), next)
    }

    @Test
    fun `calculateNextOccurrence Monthly returns same day next month`() {
        val next = RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.Monthly, now = jan1())
        assertEquals(Instant.parse("2024-02-01T10:00:00Z"), next)
    }

    @Test
    fun `calculateNextOccurrence None returns null`() {
        assertNull(RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.None, now = jan1()))
    }

    // ===== Drift regression: Jan 31 monthly =====

    @Test
    fun `monthly recurrence from Jan 31 never drifts to the 28th of successive months`() {
        val anchor = jan31()

        var occurrence = anchor
        // Fire 6 times, each time completing the current occurrence.
        repeat(6) { index ->
            val next = RecurrenceEngine.calculateNextOccurrence(anchor, occurrence, RecurrenceRule.Monthly, now = occurrence)!!
            when (index) {
                0 -> assertEquals(Instant.parse("2024-02-29T10:00:00Z"), next) // Feb 2024 (leap)
                1 -> assertEquals(Instant.parse("2024-03-31T10:00:00Z"), next) // back to 31
                2 -> assertEquals(Instant.parse("2024-04-30T10:00:00Z"), next)
                3 -> assertEquals(Instant.parse("2024-05-31T10:00:00Z"), next)
                4 -> assertEquals(Instant.parse("2024-06-30T10:00:00Z"), next)
                5 -> assertEquals(Instant.parse("2024-07-31T10:00:00Z"), next)
            }
            occurrence = next
        }
    }

    @Test
    fun `monthly recurrence anchor 30 still respects 31-day months`() {
        val anchor = Instant.parse("2024-04-30T10:00:00Z")
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Monthly, now = anchor)!!
        assertEquals(Instant.parse("2024-05-30T10:00:00Z"), next)
        // From May 30, next is Jun 30 (not Jun 29/28).
        val jun = RecurrenceEngine.calculateNextOccurrence(anchor, next, RecurrenceRule.Monthly, now = next)!!
        assertEquals(Instant.parse("2024-06-30T10:00:00Z"), jun)
    }

    // ===== Leap year: Feb 29 =====

    @Test
    fun `yearly recurrence from Feb 29 2024 lands Feb 28 in non-leap years`() {
        val anchor = feb29_2024()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Yearly, now = anchor)!!
        assertEquals(Instant.parse("2025-02-28T10:00:00Z"), next)
        // Next after Feb 28 2025 is Feb 28 2026 (still non-leap).
        val nextAgain = RecurrenceEngine.calculateNextOccurrence(anchor, next, RecurrenceRule.Yearly, now = next)!!
        assertEquals(Instant.parse("2026-02-28T10:00:00Z"), nextAgain)
        // And lands back on Feb 29 in 2028 (leap).
        val leapBack = RecurrenceEngine.calculateNextOccurrence(anchor, nextAgain, RecurrenceRule.Yearly, now = nextAgain)!!
        assertEquals(Instant.parse("2027-02-28T10:00:00Z"), leapBack)
    }

    @Test
    fun `yearly recurrence from a normal date keeps the same day`() {
        val anchor = jan1()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Yearly, now = anchor)!!
        assertEquals(Instant.parse("2025-01-01T10:00:00Z"), next)
    }

    @Test
    fun `monthly leap rollover from Jan 31 2024 goes to Feb 29`() {
        val anchor = jan31()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Monthly, now = anchor)!!
        assertEquals(Instant.parse("2024-02-29T10:00:00Z"), next)
    }

    @Test
    fun `yearly recurrence lands back on Feb 29 when a leap year arrives`() {
        val anchor = feb29_2024()
        var occurrence = anchor
        val expected = listOf(
            "2025-02-28T10:00:00Z",
            "2026-02-28T10:00:00Z",
            "2027-02-28T10:00:00Z",
            "2028-02-29T10:00:00Z"
        )
        expected.forEach { exp ->
            occurrence = RecurrenceEngine.calculateNextOccurrence(anchor, occurrence, RecurrenceRule.Yearly, now = occurrence)!!
            assertEquals(Instant.parse(exp), occurrence)
        }
    }

    // ===== Interval =====

    @Test
    fun `interval recurrence adds the fixed days from last occurrence`() {
        val anchor = jan1()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Interval(30), now = anchor)!!
        assertEquals(Instant.parse("2024-01-31T10:00:00Z"), next)
    }

    // ===== without anchor (legacy) =====

    @Test
    fun `legacy data without anchor uses from date as anchor`() {
        val from = Instant.parse("2024-03-28T10:00:00Z")
        val next = RecurrenceEngine.calculateNextOccurrence(from, from, RecurrenceRule.Monthly, now = from)!!
        assertEquals(Instant.parse("2024-04-28T10:00:00Z"), next)
    }

    // ===== M-12: late completion skips ahead to the future =====

    @Test
    fun `daily completed 10 days late resumes in the future on the same cadence`() {
        val anchor = Instant.parse("2026-10-20T09:00:00Z")
        val now = Instant.parse("2026-10-30T12:00:00Z") // completed 10 days late
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Daily, now = now)!!
        // Oct 21..30 occurrences are already past; the series resumes Oct 31
        // at the anchor's time of day instead of spawning a past occurrence.
        assertEquals(Instant.parse("2026-10-31T09:00:00Z"), next)
        assertTrue(next.isAfter(now))
    }

    @Test
    fun `weekly completed late resumes on the next anchor weekday in the future`() {
        val anchor = Instant.parse("2026-10-05T09:00:00Z") // Monday
        val now = Instant.parse("2026-10-30T12:00:00Z") // Friday, 25 days late
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Weekly, now = now)!!
        // Mondays Oct 12/19/26 are already past; next future slot is Monday Nov 2.
        assertEquals(Instant.parse("2026-11-02T09:00:00Z"), next)
        assertTrue(next.isAfter(now))
    }

    @Test
    fun `monthly completed months late keeps the anchor day clamping`() {
        val anchor = Instant.parse("2026-01-31T10:00:00Z")
        val now = Instant.parse("2026-05-15T12:00:00Z")
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Monthly, now = now)!!
        // Slots Feb 28, Mar 31, Apr 30 are all past -> May 31 (clamped grid).
        assertEquals(Instant.parse("2026-05-31T10:00:00Z"), next)
        assertTrue(next.isAfter(now))
    }

    // ===== M-11: snoozing one occurrence must not shift the anchor grid =====

    @Test
    fun `weekly snoozed to Thursday still recurs on the anchor Monday`() {
        val anchor = Instant.parse("2026-10-05T09:00:00Z") // Monday
        val snoozed = Instant.parse("2026-10-15T09:00:00Z") // Mon Oct 12 occurrence snoozed to Thu Oct 15
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, snoozed, RecurrenceRule.Weekly, now = snoozed)!!
        // Following Monday on the anchor grid, not Thursday + 1 week (Oct 22).
        assertEquals(Instant.parse("2026-10-19T09:00:00Z"), next)
    }

    @Test
    fun `interval 3 snoozed by one day stays on the anchor grid`() {
        val anchor = Instant.parse("2026-10-01T09:00:00Z") // grid: Oct 1, 4, 7, 10 …
        val snoozed = Instant.parse("2026-10-05T09:00:00Z") // Oct 4 occurrence snoozed +1 day
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, snoozed, RecurrenceRule.Interval(3), now = snoozed)!!
        // Next grid slot Oct 7, not snoozed date + 3 days (Oct 8).
        assertEquals(Instant.parse("2026-10-07T09:00:00Z"), next)
    }
}
