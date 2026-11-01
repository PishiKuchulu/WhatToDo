package com.example.pfptodo.worker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pfptodo.TodoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Rebuilds exact task reminders after a device reboot, a timezone change, or a
 * manual wall-clock change.
 *
 * AlarmManager alarms are wiped on reboot while WorkManager keeps its own queue, so the
 * exact-alarm path (setExactAndAllowWhileIdle) would silently die for every pending
 * reminder. Timezone / time-set changes do not wipe alarms, but reminders are stored as
 * epoch instants, so their local wall-clock meaning shifts and they are re-armed too.
 * scheduleReminder() re-enqueues whichever delivery path is appropriate for
 * each task, so re-running it here is idempotent and safe next to WorkManager's
 * restored queue. Birthday reminders self-heal in TodoApplication.onCreate, which runs
 * before this receiver fires (the receiver itself starts the app process).
 */
class BootReminderReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_SET -> Unit
            else -> return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as? TodoApplication ?: return@launch
                val now = Instant.now()
                app.container.taskRepository.getAllTasksRaw()
                    .filter { task ->
                        val reminder = task.reminderTime
                        reminder != null && reminder.isAfter(now) && !task.isCompleted
                    }
                    .forEach { app.container.reminderManager.scheduleReminder(it) }
            } catch (_: Exception) {
                // Best effort: a boot receiver must never crash the boot sequence.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
