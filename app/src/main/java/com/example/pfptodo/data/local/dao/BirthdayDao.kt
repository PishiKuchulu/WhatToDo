package com.example.pfptodo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pfptodo.data.local.entity.BirthdayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays ORDER BY month ASC, day ASC, name ASC")
    fun getAllBirthdays(): Flow<List<BirthdayEntity>>

    @Query("SELECT * FROM birthdays ORDER BY month ASC, day ASC, name ASC")
    suspend fun getAllBirthdaysNow(): List<BirthdayEntity>

    @Query("SELECT * FROM birthdays WHERE id = :id")
    suspend fun getBirthdayById(id: Long): BirthdayEntity?

    @Query("SELECT * FROM birthdays WHERE contactId = :contactId LIMIT 1")
    suspend fun getByContactId(contactId: String): BirthdayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBirthday(birthday: BirthdayEntity): Long

    @Update
    suspend fun updateBirthday(birthday: BirthdayEntity)

    @Delete
    suspend fun deleteBirthday(birthday: BirthdayEntity)

    @Query("DELETE FROM birthdays WHERE id = :id")
    suspend fun deleteById(id: Long)
}
