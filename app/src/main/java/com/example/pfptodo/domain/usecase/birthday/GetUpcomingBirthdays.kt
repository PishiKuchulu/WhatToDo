package com.example.pfptodo.domain.usecase.birthday

import com.example.pfptodo.core.util.dayTickerFlow
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.repository.BirthdayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate

/**
 * A birthday projected onto its next occurrence, ready for display.
 */
data class UpcomingBirthday(
    val birthday: Birthday,
    val occurrence: LocalDate,
    val daysUntil: Long,
    val age: Int?
) {
    val isToday: Boolean get() = daysUntil == 0L
}

/**
 * Projects every stored birthday onto its next occurrence and orders them by
 * how soon they happen. Pure given a [Clock]; the flow wrapper is the only
 * side-effecting part.
 */
class GetUpcomingBirthdays(
    private val repository: BirthdayRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    // Combine with the day ticker so "days until" / isToday roll over at local
    // midnight even when the stored birthdays do not change (M-14).
    operator fun invoke(): Flow<List<UpcomingBirthday>> =
        combine(repository.getAllBirthdays(), dayTickerFlow(clock)) { birthdays, today ->
            project(birthdays, today)
        }

    /** Pure projection — exposed for deterministic testing. */
    fun project(birthdays: List<Birthday>, today: LocalDate): List<UpcomingBirthday> =
        birthdays
            .map { b ->
                val occurrence = b.nextOccurrence(today)
                UpcomingBirthday(
                    birthday = b,
                    occurrence = occurrence,
                    daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, occurrence),
                    age = b.ageAt(occurrence)
                )
            }
            .sortedWith(compareBy({ it.daysUntil }, { it.birthday.name }))
}
