package com.example.pfptodo.worker.reminder

import com.example.pfptodo.data.local.entity.BirthdayEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure reminder-decision logic extracted from [BirthdayReminderWorker].
 *
 * The rest of [BirthdayReminderWorker.doWork] (database lookup, notification
 * posting, WorkManager rescheduling) needs an Android runtime and cannot run
 * on the host JVM without Robolectric.
 */
class BirthdayReminderWorkerDecisionTest {

    private fun birthday(id: Long) = BirthdayEntity(
        id = id,
        name = "Ali",
        month = 5,
        day = 10
    )

    @Test
    fun `existing birthday notifies`() {
        assertTrue(BirthdayReminderWorker.shouldNotify(birthday(id = 1L)))
    }

    @Test
    fun `birthday with id zero does not notify`() {
        assertFalse(BirthdayReminderWorker.shouldNotify(birthday(id = 0L)))
    }

    @Test
    fun `birthday with negative id does not notify`() {
        assertFalse(BirthdayReminderWorker.shouldNotify(birthday(id = -3L)))
    }
}
