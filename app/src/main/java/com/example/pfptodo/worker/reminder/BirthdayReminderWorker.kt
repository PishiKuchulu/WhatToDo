package com.example.pfptodo.worker.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pfptodo.data.local.PishiDatabase
import com.example.pfptodo.data.local.entity.BirthdayEntity
import com.example.pfptodo.data.mapper.toDomain

class BirthdayReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val birthdayId = inputData.getLong(BirthdayReminderManagerImpl.KEY_ID, -1L)
        if (birthdayId <= 0L) return Result.failure()
        val isEve = inputData.getBoolean(BirthdayReminderManagerImpl.KEY_EVE, false)

        val database = PishiDatabase.getDatabase(applicationContext)
        val birthday = database.birthdayDao().getBirthdayById(birthdayId)
            ?: return Result.success()

        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            val helper = NotificationHelper(applicationContext)
            helper.createBirthdayChannel()
            helper.showBirthdayNotification(birthday.id, birthday.name, isEve)
        }

        // Birthdays repeat every year: queue the next occurrence now. (A one-shot request
        // used to fire once and then never again.) The eve worker only refreshes the pair
        // when the main reminder fires, so skip rescheduling from it.
        if (!isEve) {
            BirthdayReminderManagerImpl(applicationContext).scheduleReminder(birthday.toDomain())
        }
        return Result.success()
    }

    companion object {
        /** Reminders always notify — the record existing is the only precondition. */
        fun shouldNotify(birthday: BirthdayEntity): Boolean = birthday.id > 0L
    }
}
