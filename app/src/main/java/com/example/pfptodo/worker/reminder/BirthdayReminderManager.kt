package com.example.pfptodo.worker.reminder

import com.example.pfptodo.domain.model.Birthday

interface BirthdayReminderManager {
    /**
     * Schedules the next upcoming reminder(s) for a saved birthday. No-op if id <= 0.
     * With [replace] = false an already-pending reminder is left untouched (used at app
     * start-up to self-heal reminders without disturbing healthy ones).
     */
    fun scheduleReminder(birthday: Birthday, replace: Boolean = true)

    /** Cancels any pending reminder for the birthday id. */
    fun cancelReminder(birthdayId: Long)
}
