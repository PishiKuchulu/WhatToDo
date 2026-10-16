package com.example.pfptodo.domain.util

import com.example.pfptodo.domain.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecurrenceEngineTest {

    // ===== helpers =====

    private fun jan31(): Instant = Instant.parse("2024-01-31T10:00:00Z")
    private fun feb29_2024(): Instant = Instant.parse("2024-02-29T10:00:00Z")
    private fun feb28_2024(): Instant = Instant.parse("2024-02-28T10:00:00Z")
    private fun jan1(): Instant = Instant.parse("2024-01-01T10:00:00Z")

    // ===== Daily / Weekly / Interval (anchor-independent) =====

    @Test
    fun `calculateNextOccurrence Daily returns next day`() {
        val next = RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.Daily)
        assertEquals(Instant.parse("2024-01-02T10:00:00Z"), next)
    }

    @Test
    fun `calculateNextOccurrence Weekly returns next week`() {
        val next = RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.Weekly)
        assertEquals(Instant.parse("2024-01-08T10:00:00Z"), next)
    }

    @Test
    fun `calculateNextOccurrence Monthly returns same day next month`() {
        val next = RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.Monthly)
        assertEquals(Instant.parse("2024-02-01T10:00:00Z"), next)
    }

    @Test
    fun `calculateNextOccurrence None returns null`() {
        assertNull(RecurrenceEngine.calculateNextOccurrence(jan1(), jan1(), RecurrenceRule.None))
    }

    // ===== Drift regression: Jan 31 monthly =====

    @Test
    fun `monthly recurrence from Jan 31 never drifts to the 28th of successive months`() {
        val anchor = jan31()

        var occurrence = anchor
        // Fire 6 times, each time completing the current occurrence.
        repeat(6) { index ->
            val next = RecurrenceEngine.calculateNextOccurrence(anchor, occurrence, RecurrenceRule.Monthly)!!
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
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Monthly)!!
        assertEquals(Instant.parse("2024-05-30T10:00:00Z"), next)
        // From May 30, next is Jun 30 (not Jun 29/28).
        val jun = RecurrenceEngine.calculateNextOccurrence(anchor, next, RecurrenceRule.Monthly)!!
        assertEquals(Instant.parse("2024-06-30T10:00:00Z"), jun)
    }

    // ===== Leap year: Feb 29 =====

    @Test
    fun `yearly recurrence from Feb 29 2024 lands Feb 28 in non-leap years`() {
        val anchor = feb29_2024()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Yearly)!!
        assertEquals(Instant.parse("2025-02-28T10:00:00Z"), next)
        // Next after Feb 28 2025 is Feb 28 2026 (still non-leap).
        val nextAgain = RecurrenceEngine.calculateNextOccurrence(anchor, next, RecurrenceRule.Yearly)!!
        assertEquals(Instant.parse("2026-02-28T10:00:00Z"), nextAgain)
        // And lands back on Feb 29 in 2028 (leap).
        val leapBack = RecurrenceEngine.calculateNextOccurrence(anchor, nextAgain, RecurrenceRule.Yearly)!!
        assertEquals(Instant.parse("2027-02-28T10:00:00Z"), leapBack)
    }

    @Test
    fun `yearly recurrence from a normal date keeps the same day`() {
        val anchor = jan1()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Yearly)!!
        assertEquals(Instant.parse("2025-01-01T10:00:00Z"), next)
    }

    @Test
    fun `monthly leap rollover from Jan 31 2024 goes to Feb 29`() {
        val anchor = jan31()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Monthly)!!
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
            occurrence = RecurrenceEngine.calculateNextOccurrence(anchor, occurrence, RecurrenceRule.Yearly)!!
            assertEquals(Instant.parse(exp), occurrence)
        }
    }

    // ===== Interval =====

    @Test
    fun `interval recurrence adds the fixed days from last occurrence`() {
        val anchor = jan1()
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Interval(30))!!
        assertEquals(Instant.parse("2024-01-31T10:00:00Z"), next)
    }

    // ===== without anchor (legacy) =====

    @Test
    fun `legacy data without anchor uses from date as anchor`() {
        val from = Instant.parse("2024-03-28T10:00:00Z")
        val next = RecurrenceEngine.calculateNextOccurrence(from, from, RecurrenceRule.Monthly)!!
        assertEquals(Instant.parse("2024-04-28T10:00:00Z"), next)
    }
}