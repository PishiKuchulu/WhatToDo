package com.example.pfptodo.domain.usecase.birthday

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.repository.BirthdayRepository
import kotlinx.coroutines.flow.Flow

/**
 * Wrapper class containing all birthday-related use cases.
 */
data class BirthdayUseCases(
    val getAllBirthdays: GetAllBirthdays,
    val getUpcomingBirthdays: GetUpcomingBirthdays,
    val getBirthdayById: GetBirthdayById,
    val addBirthday: AddBirthday,
    val updateBirthday: UpdateBirthday,
    val deleteBirthday: DeleteBirthday,
    val validateBirthday: ValidateBirthday = ValidateBirthday()
)

class GetAllBirthdays(private val repository: BirthdayRepository) {
    operator fun invoke(): Flow<List<Birthday>> = repository.getAllBirthdays()
}

class GetBirthdayById(private val repository: BirthdayRepository) {
    suspend operator fun invoke(id: Long): Result<Birthday?> = repository.getBirthdayById(id)
}

class AddBirthday(private val repository: BirthdayRepository) {
    suspend operator fun invoke(birthday: Birthday): Result<Long> =
        repository.insertBirthday(birthday)
}

class UpdateBirthday(private val repository: BirthdayRepository) {
    suspend operator fun invoke(birthday: Birthday): Result<Unit> =
        repository.updateBirthday(birthday)
}

class DeleteBirthday(private val repository: BirthdayRepository) {
    suspend operator fun invoke(birthday: Birthday): Result<Unit> =
        repository.deleteBirthday(birthday)
}

/**
 * Pure validation for a birthday record. Returns an error message key token, or null when valid.
 */
class ValidateBirthday {
    /**
     * @return null when valid, otherwise one of: "name_blank", "bad_month", "bad_day",
     *         "bad_year", or "future_date".
     */
    operator fun invoke(
        name: String,
        month: Int,
        day: Int,
        birthYear: Int?,
        today: java.time.LocalDate
    ): String? {
        if (name.isBlank()) return "name_blank"
        if (month !in 1..12) return "bad_month"
        val maxDay = java.time.Month.of(month).maxLength()
        if (day !in 1..maxDay) return "bad_day"
        if (birthYear != null) {
            if (birthYear < Birthday.MIN_YEAR) return "bad_year"
            // A birth date in the future is impossible.
            if (birthYear > today.year) return "future_date"
            if (birthYear == today.year && month > today.monthValue) return "future_date"
            if (birthYear == today.year && month == today.monthValue && day > today.dayOfMonth) {
                return "future_date"
            }
        }
        return null
    }
}
