package com.example.pfptodo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "birthdays",
    indices = [Index("name")]
)
data class BirthdayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Month (1-12) in the Gregorian calendar. */
    val month: Int,
    /** Day of month (1-31) in the Gregorian calendar. */
    val day: Int,
    /** Birth year, when known — enables age display. Null means year unknown. */
    val birthYear: Int? = null,
    val notes: String? = null,
    /**
     * Local time-of-day for the reminder, in minutes since midnight.
     * Defaults to 540 (09:00).
     */
    val reminderMinutesOfDay: Int = DEFAULT_REMINDER_MINUTES,
    /** Whether to also fire a reminder the day before. */
    val notifyOnDayBefore: Boolean = false,
    /** Source contact id when imported from the device address book, else null. */
    val contactId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTES = 540
    }
}
