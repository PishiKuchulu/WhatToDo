package com.example.pfptodo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceRuleTest {

    @Test
    fun `fromString parses named rules and none`() {
        assertEquals(RecurrenceRule.None, RecurrenceRule.fromString(null))
        assertEquals(RecurrenceRule.None, RecurrenceRule.fromString(""))
        assertEquals(RecurrenceRule.None, RecurrenceRule.fromString("NONE"))
        assertEquals(RecurrenceRule.Daily, RecurrenceRule.fromString("DAILY"))
        assertEquals(RecurrenceRule.Weekly, RecurrenceRule.fromString("WEEKLY"))
        assertEquals(RecurrenceRule.Monthly, RecurrenceRule.fromString("MONTHLY"))
        assertEquals(RecurrenceRule.Yearly, RecurrenceRule.fromString("YEARLY"))
        assertEquals(RecurrenceRule.None, RecurrenceRule.fromString("SOMETHING_ELSE"))
    }

    @Test
    fun `fromString parses positive intervals`() {
        assertEquals(RecurrenceRule.Interval(1), RecurrenceRule.fromString("INTERVAL=1"))
        assertEquals(RecurrenceRule.Interval(3), RecurrenceRule.fromString("INTERVAL=3"))
        assertEquals(RecurrenceRule.Interval(30), RecurrenceRule.fromString("INTERVAL=30"))
    }

    @Test
    fun `fromString rejects intervals below 1 as no recurrence`() {
        // M-13: INTERVAL=0 / negative values used to parse as a valid rule that
        // could never advance; they are invalid and must become None.
        assertEquals(RecurrenceRule.None, RecurrenceRule.fromString("INTERVAL=0"))
        assertEquals(RecurrenceRule.None, RecurrenceRule.fromString("INTERVAL=-1"))
        assertEquals(RecurrenceRule.None, RecurrenceRule.fromString("INTERVAL=-30"))
    }
}
