package com.example.pfptodo.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Small buffer past midnight so the new day is fully in effect when we re-emit. */
private const val MIDNIGHT_EPSILON_MS = 1_000L

/**
 * Emits the current local date immediately, then re-emits at every subsequent
 * local midnight.
 *
 * Date-boundary logic (today / tomorrow / overdue / upcoming-birthday windows)
 * is otherwise only recomputed when the underlying data flow emits, so a screen
 * left open across midnight would keep showing yesterday's boundaries. Combining
 * a data flow with this ticker makes those boundaries roll over on their own.
 *
 * The [clock] is injectable for tests; its zone defines "local" midnight.
 */
fun dayTickerFlow(clock: Clock = Clock.systemDefaultZone()): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now(clock)
        emit(today)
        val zone = clock.zone ?: ZoneId.systemDefault()
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        val waitMs = Duration.between(clock.instant(), nextMidnight).toMillis() + MIDNIGHT_EPSILON_MS
        delay(waitMs.coerceAtLeast(1L))
    }
}
