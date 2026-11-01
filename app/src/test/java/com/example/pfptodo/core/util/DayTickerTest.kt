package com.example.pfptodo.core.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayTickerTest {

    /** A [Clock] whose instant the test moves by hand. */
    private class MutableClock(
        var currentInstant: Instant,
        private val currentZone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = currentZone
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant
    }

    @Test
    fun `emits today immediately then re-emits after local midnight`() = runTest {
        val zone = ZoneId.of("UTC")
        val clock = MutableClock(Instant.parse("2026-10-22T23:59:30Z"), zone)

        val collected = mutableListOf<LocalDate>()
        val job = launch { dayTickerFlow(clock).collect { collected.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 22)), collected)

        // Move the wall clock past midnight, then let the scheduled wakeup
        // fire (30 s to midnight + 1 s epsilon, in virtual time).
        clock.currentInstant = Instant.parse("2026-10-23T00:00:02Z")
        advanceTimeBy(32_000)
        runCurrent()
        assertEquals(
            listOf(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23)),
            collected
        )
        job.cancelAndJoin()
    }

    @Test
    fun `does not re-emit before midnight`() = runTest {
        val zone = ZoneId.of("UTC")
        val clock = MutableClock(Instant.parse("2026-10-22T12:00:00Z"), zone)

        val collected = mutableListOf<LocalDate>()
        val job = launch { dayTickerFlow(clock).collect { collected.add(it) } }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 22)), collected)
        job.cancelAndJoin()
    }
}
