package com.example.pfptodo.worker.reminder

import com.example.pfptodo.data.local.entity.TaskEntity
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests the pure reminder-decision logic extracted from [ReminderWorker].
 */
class ReminderWorkerDecisionTest {

    private fun taskEntity(
        isCompleted: Boolean,
        reminderTime: Instant?
    ) = TaskEntity(
        id = 1L,
        title = "Test",
        isCompleted = isCompleted,
        reminderTime = reminderTime,
        priority = Priority.MEDIUM,
        recurrenceRule = RecurrenceRule.None
    )

    @Test
    fun `completed task with reminder does not notify`() {
        val task = taskEntity(isCompleted = true, reminderTime = Instant.now().plusSeconds(3600))
        assertFalse(ReminderWorker.shouldNotify(task))
    }

    @Test
    fun `active task with reminder notifies`() {
        val task = taskEntity(isCompleted = false, reminderTime = Instant.now().plusSeconds(3600))
        assertTrue(ReminderWorker.shouldNotify(task))
    }

    @Test
    fun `task without reminder does not notify`() {
        val task = taskEntity(isCompleted = false, reminderTime = null)
        assertFalse(ReminderWorker.shouldNotify(task))
    }

    @Test
    fun `completed task without reminder does not notify`() {
        val task = taskEntity(isCompleted = true, reminderTime = null)
        assertFalse(ReminderWorker.shouldNotify(task))
    }
}