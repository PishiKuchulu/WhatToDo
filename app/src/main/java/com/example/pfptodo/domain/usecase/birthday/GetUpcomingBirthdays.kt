package com.example.pfptodo.domain.usecase.birthday

import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.repository.BirthdayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    operator fun invoke(): Flow<List<UpcomingBirthday>> =
        repository.getAllBirthdays().map { project(it, LocalDate.now(clock)) }

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
