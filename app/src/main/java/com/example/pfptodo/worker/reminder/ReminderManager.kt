package com.example.pfptodo.worker.reminder

import com.example.pfptodo.domain.model.Task

interface ReminderManager {
    /** Schedules (or reschedules) the reminder for a saved task. No-op if no reminder or id <= 0. */
    fun scheduleReminder(task: Task)

    /** Cancels any pending reminder for the task id. */
    fun cancelReminder(taskId: Long)
}