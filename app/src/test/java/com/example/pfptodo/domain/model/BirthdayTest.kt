package com.example.pfptodo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BirthdayTest {

    private fun birthday(month: Int, day: Int, year: Int? = null) =
        Birthday(name = "Test", month = month, day = day, birthYear = year)

    @Test
    fun nextOccurrence_isToday_whenDateMatches() {
        val b = birthday(6, 15)
        val today = LocalDate.of(2026, 6, 15)
        assertEquals(today, b.nextOccurrence(today))
        assertEquals(0L, b.daysUntilNext(today))
    }

    @Test
    fun nextOccurrence_rollsToNextYear_whenAlreadyPassed() {
        val b = birthday(6, 15)
        val from = LocalDate.of(2026, 6, 16)
        assertEquals(LocalDate.of(2027, 6, 15), b.nextOccurrence(from))
    }

    @Test
    fun nextOccurrence_handlesEndOfYear() {
        val b = birthday(12, 31)
        assertEquals(LocalDate.of(2026, 12, 31), b.nextOccurrence(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2027, 12, 31), b.nextOccurrence(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun leapDay_fallsBackToFeb28_inNonLeapYear() {
        val b = birthday(2, 29)
        assertEquals(LocalDate.of(2026, 2, 28), b.occurrenceIn(2026))
        assertEquals(LocalDate.of(2028, 2, 29), b.occurrenceIn(2028))
    }

    @Test
    fun leapDay_nextOccurrence_afterFeb28InNonLeapYear_usesFollowingYear() {
        val b = birthday(2, 29)
        // In 2026 (non-leap), the Feb 29 birthday is observed on Feb 28.
        assertEquals(LocalDate.of(2026, 2, 28), b.nextOccurrence(LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2027, 2, 28), b.nextOccurrence(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun ageAt_isNull_whenBirthYearUnknown() {
        assertNull(birthday(6, 15).ageAt(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun ageAt_computesYearDifference() {
        val b = birthday(6, 15, 1990)
        assertEquals(36, b.ageAt(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun monthDay_isNormalised() {
        assertEquals(java.time.MonthDay.of(2, 29), birthday(2, 29).monthDay)
    }
}
