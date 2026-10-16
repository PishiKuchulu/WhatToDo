package com.example.pfptodo.worker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Receives exact AlarmManager reminder alarms and hands delivery to [ReminderWorker].
 *
 * All notification building lives in [NotificationHelper]; this receiver only enqueues a
 * unique work request under the same "reminder_$taskId" name as the WorkManager path, so
 * the worker's suppression checks (deleted task, completed task, disabled notifications)
 * still apply and cancelReminder keeps working regardless of the delivery path used.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return

        // ReminderWorker re-reads the task row from the DB, so only the id is needed.
        val data = Data.Builder()
            .putLong("taskId", taskId)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ReminderManagerImpl.workNameFor(taskId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        const val ACTION_REMIND = "com.example.pfptodo.action.REMINDER_ALARM"
        const val EXTRA_TASK_ID = "taskId"
    }
}
