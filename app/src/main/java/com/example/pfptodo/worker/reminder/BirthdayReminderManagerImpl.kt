package com.example.pfptodo.worker.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pfptodo.domain.model.Birthday
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class BirthdayReminderManagerImpl(private val context: Context) : BirthdayReminderManager {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun scheduleReminder(birthday: Birthday, replace: Boolean) {
        if (birthday.id <= 0L) return

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)

        // First occurrence whose reminder time is still in the future. If today's reminder
        // time has already passed, this rolls over to next year instead of silently doing
        // nothing (which used to leave the birthday without any reminder).
        var occurrence = birthday.nextOccurrence(today)
        var fireAt = fireInstant(birthday, occurrence, zone)
        if (!fireAt.isAfter(now)) {
            occurrence = birthday.nextOccurrence(occurrence.plusDays(1))
            fireAt = fireInstant(birthday, occurrence, zone)
        }
        schedule(birthday, fireAt, workName(birthday.id), isEve = false, replace = replace)

        val eveWork = workName(birthday.id) + "_eve"
        if (birthday.notifyOnDayBefore) {
            // Day-before reminder for the same occurrence, or the following one if it already passed.
            // Previous *calendar* day at the same local time — NOT minus 24h — so a DST
            // transition between the two days does not shift the reminder by an hour.
            var eveAt = fireAt.atZone(zone).minusDays(1).toInstant()
            if (!eveAt.isAfter(now)) {
                val following = birthday.nextOccurrence(occurrence.plusDays(1))
                eveAt = fireInstant(birthday, following, zone).atZone(zone).minusDays(1).toInstant()
            }
            schedule(birthday, eveAt, eveWork, isEve = true, replace = replace)
        } else {
            workManager.cancelUniqueWork(eveWork)
        }
    }

    private fun fireInstant(birthday: Birthday, occurrence: LocalDate, zone: ZoneId): Instant =
        occurrence
            .atTime(birthday.reminderMinutesOfDay / 60, birthday.reminderMinutesOfDay % 60)
            .atZone(zone)
            .toInstant()

    private fun schedule(birthday: Birthday, fireAt: Instant, workName: String, isEve: Boolean, replace: Boolean) {
        val delay = Duration.between(Instant.now(), fireAt)
        if (delay.isNegative) return

        val data = Data.Builder()
            .putLong(KEY_ID, birthday.id)
            .putString(KEY_NAME, birthday.name)
            .putBoolean(KEY_EVE, isEve)
            .build()

        val request = OneTimeWorkRequestBuilder<BirthdayReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            workName,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelReminder(birthdayId: Long) {
        if (birthdayId <= 0L) return
        workManager.cancelUniqueWork(workName(birthdayId))
        workManager.cancelUniqueWork(workName(birthdayId) + "_eve")
    }

    companion object {
        const val KEY_ID = "birthdayId"
        const val KEY_NAME = "name"
        const val KEY_EVE = "isEve"
        private fun workName(id: Long) = "birthday_$id"
    }
}
