package com.example.pfptodo.domain.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Birthday
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing [Birthday] records.
 */
interface BirthdayRepository {
    fun getAllBirthdays(): Flow<List<Birthday>>

    /** One-shot snapshot of every birthday (used by backup export/import). */
    suspend fun getAllBirthdaysRaw(): List<Birthday>

    suspend fun getBirthdayById(id: Long): Result<Birthday?>

    suspend fun getByContactId(contactId: String): Result<Birthday?>

    suspend fun insertBirthday(birthday: Birthday): Result<Long>

    suspend fun updateBirthday(birthday: Birthday): Result<Unit>

    suspend fun deleteBirthday(birthday: Birthday): Result<Unit>

    suspend fun deleteById(id: Long): Result<Unit>
}
