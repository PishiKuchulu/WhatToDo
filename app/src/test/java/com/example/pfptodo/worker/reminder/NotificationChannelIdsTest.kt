package com.example.pfptodo.worker.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the notification channel IDs that [NotificationHelper] creates and that
 * the reminder workers post into.
 *
 * Channel creation and notification building themselves need an Android
 * Context/NotificationManager, so they cannot be unit-tested on the host JVM
 * without Robolectric; this only guards the shared ID contract.
 */
class NotificationChannelIdsTest {

    @Test
    fun `task reminder channel id is stable`() {
        assertEquals("task_reminder_channel", NotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `birthday reminder channel id is stable`() {
        assertEquals("birthday_reminder_channel", NotificationHelper.BIRTHDAY_CHANNEL_ID)
    }
}
