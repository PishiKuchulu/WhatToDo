package com.example.pfptodo.worker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import com.example.pfptodo.domain.model.Task
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class ReminderManagerImpl(private val context: Context) : ReminderManager {

    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val appContext: Context get() = context.applicationContext

    override fun scheduleReminder(task: Task) {
        val reminderTime = task.reminderTime
        // Never use id 0 (pre-save) as a persistent identifier.
        if (task.id <= 0L) return
        // Clear any stale delivery state first: a past or removed reminder must not
        // leave orphaned WorkManager work or alarms behind.
        cancelDelivery(task.id)
        if (reminderTime == null) return
        val delay = Duration.between(Instant.now(), reminderTime)
        if (delay.isNegative) return

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (canScheduleExactAlarms(alarmManager)) {
            try {
                // Exact path: survives Doze, fires at the user's chosen moment. Delivery
                // is handed to ReminderWorker via ReminderAlarmReceiver, which reuses
                // the same unique work name so cancellation stays path-independent.
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime.toEpochMilli(),
                    alarmPendingIntent(task.id, PendingIntent.FLAG_UPDATE_CURRENT)
                )
                return
            } catch (_: SecurityException) {
                // Exact-alarm permission was revoked at runtime — fall through to WorkManager.
            }
        }
        enqueueWorkManagerReminder(task, delay)
    }

    override fun cancelReminder(taskId: Long) {
        if (taskId <= 0L) return
        cancelDelivery(taskId)
    }

    /** Cancels both delivery channels (WorkManager unique work and the exact alarm). */
    private fun cancelDelivery(taskId: Long) {
        workManager.cancelUniqueWork(workNameFor(taskId))
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(taskId, 0))
    }

    /** Fallback path used when exact alarms are unavailable or the permission was revoked. */
    private fun enqueueWorkManagerReminder(task: Task, delay: Duration) {
        val data = Data.Builder()
            .putLong("taskId", task.id)
            .putString("title", task.title)
            .putString("description", task.description ?: "")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            workNameFor(task.id),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        // canScheduleExactAlarms() exists only on API 31+; on older APIs exact alarms
        // need no special permission, so the exact path is always usable.
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(taskId: Long, flags: Int): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMIND
            putExtra(ReminderAlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeFor(taskId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or flags
        )
    }

    companion object {
        internal fun workNameFor(taskId: Long) = "reminder_$taskId"
        private fun requestCodeFor(taskId: Long) = (taskId and 0x7fffffff).toInt()
    }
}
