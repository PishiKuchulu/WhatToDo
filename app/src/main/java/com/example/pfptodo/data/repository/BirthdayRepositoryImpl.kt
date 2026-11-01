package com.example.pfptodo.data.repository

import android.util.Log

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.data.local.dao.BirthdayDao
import com.example.pfptodo.data.mapper.toDomain
import com.example.pfptodo.data.mapper.toEntity
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.repository.BirthdayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class BirthdayRepositoryImpl(private val birthdayDao: BirthdayDao) : BirthdayRepository {

    override fun getAllBirthdays(): Flow<List<Birthday>> =
        birthdayDao.getAllBirthdays()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Log.e(TAG, "Failed to load birthdays; falling back to empty list", e)
                emit(emptyList())
            }

    override suspend fun getAllBirthdaysRaw(): List<Birthday> =
        birthdayDao.getAllBirthdaysNow().map { it.toDomain() }

    override suspend fun getBirthdayById(id: Long): Result<Birthday?> = try {
        Result.Success(birthdayDao.getBirthdayById(id)?.toDomain())
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun getByContactId(contactId: String): Result<Birthday?> = try {
        Result.Success(birthdayDao.getByContactId(contactId)?.toDomain())
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun insertBirthday(birthday: Birthday): Result<Long> = try {
        // One birthday per contact (M-18): when a NEW record carries a contactId
        // that already has a birthday, refresh that row (keeping its id and
        // createdAt) instead of inserting a duplicate. This insert is the single
        // write path shared by manual add and contact import (both reach it via
        // ViewModel.save -> AddBirthday) and by backup import, so the check
        // lives here rather than in any one caller.
        val contactId = birthday.contactId
        val existing = if (birthday.id == 0L && contactId != null) {
            birthdayDao.getByContactId(contactId)
        } else {
            null
        }
        if (existing != null) {
            birthdayDao.updateBirthday(
                birthday.copy(id = existing.id, createdAt = existing.createdAt).toEntity()
            )
            Result.Success(existing.id)
        } else {
            Result.Success(birthdayDao.insertBirthday(birthday.toEntity()))
        }
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun updateBirthday(birthday: Birthday): Result<Unit> = try {
        birthdayDao.updateBirthday(birthday.toEntity())
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun deleteBirthday(birthday: Birthday): Result<Unit> = try {
        birthdayDao.deleteBirthday(birthday.toEntity())
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun deleteById(id: Long): Result<Unit> = try {
        birthdayDao.deleteById(id)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    private companion object {
        const val TAG = "BirthdayRepositoryImpl"
    }
}
