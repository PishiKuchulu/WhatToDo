package com.example.pfptodo.worker.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pfptodo.data.local.PishiDatabase
import com.example.pfptodo.data.local.entity.TaskEntity

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong("taskId", -1L)

        if (taskId <= 0L) return Result.failure()

        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            // Permission denied or disabled — do not crash, just skip.
            return Result.success()
        }

        val database = PishiDatabase.getDatabase(applicationContext)
        val task = database.taskDao().getTaskById(taskId)

        // Task was deleted (or no longer has a reminder) before fire time — suppress.
        if (task == null) return Result.success()

        if (!shouldNotify(task)) return Result.success()

        val helper = NotificationHelper(applicationContext)
        helper.createNotificationChannel(force = false)
        helper.showNotification(
            taskId = task.id,
            title = task.title,
            content = task.description ?: ""
        )
        return Result.success()
    }

    companion object {
        /**
         * Pure decision: a reminder should notify only when the task still has a
         * reminder configured AND is not completed.
         */
        fun shouldNotify(task: TaskEntity): Boolean {
            if (task.reminderTime == null) return false
            if (task.isCompleted) return false
            return true
        }
    }
}