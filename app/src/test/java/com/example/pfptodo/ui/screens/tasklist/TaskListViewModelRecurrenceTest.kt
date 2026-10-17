package com.example.pfptodo.ui.screens.tasklist

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.TagRepository
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.usecase.task.ToggleTaskCompletionUseCase
import com.example.pfptodo.domain.util.RecurrenceEngine
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Verifies the anchor-based recurrence behavior exercised through the real
 * [ToggleTaskCompletionUseCase]: completing a recurring task spawns the next
 * occurrence via [RecurrenceEngine], preserving the series anchor, subtasks,
 * tags, and reminder preference. Uses in-memory fakes — no mocks of the logic
 * under test.
 */
class TaskListViewModelRecurrenceTest {

    // ------------------------------------------------------------------
    // In-memory fakes
    // ------------------------------------------------------------------

    private class InMemoryTaskRepository : TaskRepository {
        private val tasks = mutableListOf<Task>()
        private var nextId = 1L

        fun seed(task: Task): Task {
            val withId = if (task.id == 0L) task.copy(id = nextId++) else task
            nextId = maxOf(nextId, withId.id + 1)
            tasks.removeAll { it.id == withId.id }
            tasks.add(withId)
            return withId
        }

        fun all(): List<Task> = tasks.toList()

        override fun getAllMainTasks(): Flow<List<Task>> =
            flowOf(tasks.filter { it.parentTaskId == null })

        override fun searchTasks(query: String): Flow<List<Task>> = flowOf(emptyList())

        override suspend fun getTaskById(id: Long): Result<Task?> =
            Result.Success(tasks.find { it.id == id })

        override fun observeTaskById(id: Long): Flow<Task?> =
            flowOf(tasks.find { it.id == id })

        override fun getSubtasksForTask(parentId: Long): Flow<List<Task>> =
            flowOf(tasks.filter { it.parentTaskId == parentId })

        override suspend fun getAllTasksRaw(): List<Task> = tasks.toList()

        override suspend fun insertTask(task: Task): Result<Long> {
            val id = nextId++
            tasks.add(task.copy(id = id))
            return Result.Success(id)
        }

        override suspend fun updateTask(task: Task): Result<Unit> {
            val index = tasks.indexOfFirst { it.id == task.id }
            if (index >= 0) tasks[index] = task else tasks.add(task)
            return Result.Success(Unit)
        }

        override suspend fun deleteTask(task: Task): Result<Unit> {
            tasks.removeAll { it.id == task.id }
            return Result.Success(Unit)
        }

        override suspend fun updateTaskOrder(id: Long, orderIndex: Int): Result<Unit> =
            Result.Success(Unit)

        override suspend fun swapTaskOrder(idA: Long, idB: Long): Result<Unit> =
            Result.Success(Unit)
    }

    private class FakeTagRepository : TagRepository {
        val assignments = mutableMapOf<Long, List<Long>>()

        override fun getAllTags(): Flow<List<Tag>> = flowOf(emptyList())
        override suspend fun getAllTagsRaw(): List<Tag> = emptyList()
        override suspend fun getTagById(id: Long): Result<Tag?> = Result.Success(null)
        override suspend fun getTagByName(name: String): Result<Tag?> = Result.Success(null)
        override suspend fun insertTag(tag: Tag): Result<Long> = Result.Success(1L)
        override suspend fun updateTag(tag: Tag): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteTag(tag: Tag): Result<Unit> = Result.Success(Unit)
        override fun getTagsForTask(taskId: Long): Flow<List<Tag>> = flowOf(emptyList())
        override suspend fun setTaskTags(taskId: Long, tagIds: List<Long>): Result<Unit> {
            assignments[taskId] = tagIds
            return Result.Success(Unit)
        }

        override suspend fun getAllTaskTagCrossRefsRaw(): List<Pair<Long, Long>> = emptyList()
        override suspend fun insertTaskTagCrossRef(taskId: Long, tagId: Long): Result<Unit> =
            Result.Success(Unit)
    }

    private class RecordingReminderManager : ReminderManager {
        val scheduled = mutableListOf<Task>()
        val cancelled = mutableListOf<Long>()

        override fun scheduleReminder(task: Task) {
            scheduled.add(task)
        }

        override fun cancelReminder(taskId: Long) {
            cancelled.add(taskId)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private val zone = ZoneId.systemDefault()
    private fun instantOn(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private data class Harness(
        val tasks: InMemoryTaskRepository = InMemoryTaskRepository(),
        val tags: FakeTagRepository = FakeTagRepository(),
        val reminders: RecordingReminderManager = RecordingReminderManager()
    ) {
        val useCase = ToggleTaskCompletionUseCase(tasks, reminders, tags)
    }

    // ------------------------------------------------------------------
    // Engine behavior (kept: they call the real RecurrenceEngine)
    // ------------------------------------------------------------------

    @Test
    fun `next occurrence with anchor preserves reminder time-of-day and keeps anchor`() {
        val anchorDate = LocalDate.of(2024, 1, 31)
        val fromDate = LocalDate.of(2024, 2, 29)
        val anchorTime = LocalTime.of(10, 0)

        val anchor = instantOn(anchorDate, anchorTime)
        val from = instantOn(fromDate, anchorTime)

        // Recurring monthly — must land back on Mar 31 (not Mar 28) thanks to anchor.
        val next = RecurrenceEngine.calculateNextOccurrence(anchor, from, RecurrenceRule.Monthly)
        assertEquals(instantOn(LocalDate.of(2024, 3, 31), anchorTime), next)
    }

    @Test
    fun `legacy task without anchor degrades gracefully`() {
        // No anchor provided: the engine treats `from` as the anchor (old behavior
        // for data migrated without an anchor) and never crashes.
        val from = instantOn(LocalDate.of(2024, 3, 28), LocalTime.of(10, 0))
        val next = RecurrenceEngine.calculateNextOccurrence(from, from, RecurrenceRule.Monthly)
        // From Mar 28 with no anchor → April 28 (single-step, no drift yet).
        assertEquals(instantOn(LocalDate.of(2024, 4, 28), LocalTime.of(10, 0)), next)
    }

    // ------------------------------------------------------------------
    // Real production path: ToggleTaskCompletionUseCase.generateNextOccurrence
    // ------------------------------------------------------------------

    @Test
    fun `completing recurring task with reminder spawns occurrence preserving anchor, subtasks, tags and reminder`() =
        runTest {
            val h = Harness()
            val anchor = instantOn(LocalDate.of(2024, 6, 1), LocalTime.of(9, 0))
            val tagA = Tag(id = 11, name = "Urgent")
            val tagB = Tag(id = 22, name = "Home")

            val original = h.tasks.seed(
                Task(
                    id = 5L,
                    title = "Repeating",
                    recurrenceRule = RecurrenceRule.Weekly,
                    dueDate = anchor,
                    recurrenceAnchor = anchor,
                    reminderTime = anchor.minusSeconds(3600),
                    tags = listOf(tagA, tagB)
                )
            )
            val sub1 = h.tasks.seed(Task(title = "checklist item 1", parentTaskId = original.id))
            val sub2 = h.tasks.seed(
                Task(title = "checklist item 2", parentTaskId = original.id, isCompleted = true)
            )

            val result = h.useCase(original)

            assertTrue(result is Result.Success)

            val expectedNextDue =
                RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Weekly)
            val spawned = h.tasks.all().single { it.id != original.id && it.parentTaskId == null }

            assertFalse("spawned occurrence must be open", spawned.isCompleted)
            assertEquals("next due date comes from the real engine", expectedNextDue, spawned.dueDate)
            assertEquals(
                "reminder offset is preserved on the next occurrence",
                expectedNextDue!!.minusSeconds(3600),
                spawned.reminderTime
            )
            assertEquals("series anchor must be preserved", anchor, spawned.recurrenceAnchor)

            // Subtasks copied onto the new occurrence, reset to open, new ids.
            val spawnedSubs = h.tasks.all().filter { it.parentTaskId == spawned.id }
            assertEquals(2, spawnedSubs.size)
            assertEquals(
                setOf(sub1.title, sub2.title),
                spawnedSubs.map { it.title }.toSet()
            )
            assertTrue("copied subtasks must be reset to open", spawnedSubs.all { !it.isCompleted })
            assertTrue(
                "copied subtasks must be new rows",
                spawnedSubs.none { it.id == sub1.id || it.id == sub2.id }
            )

            // Tags preserved on the new occurrence.
            assertEquals(listOf(tagA.id, tagB.id), h.tags.assignments[spawned.id])

            // Reminder lifecycle on the real path: completion cancels the old
            // reminder, the spawned occurrence gets its own.
            assertTrue(h.reminders.cancelled.contains(original.id))
            val scheduledForSpawn = h.reminders.scheduled.single { it.id == spawned.id }
            assertEquals(
                spawned.dueDate!!.minusSeconds(3600),
                scheduledForSpawn.reminderTime
            )
        }

    @Test
    fun `completing recurring task without reminder spawns occurrence with no reminder`() =
        runTest {
            val h = Harness()
            val anchor = instantOn(LocalDate.of(2024, 6, 1), LocalTime.of(9, 0))

            val original = h.tasks.seed(
                Task(
                    id = 7L,
                    title = "No reminder series",
                    recurrenceRule = RecurrenceRule.Daily,
                    dueDate = anchor,
                    recurrenceAnchor = anchor,
                    reminderTime = null
                )
            )

            val result = h.useCase(original)

            assertTrue(result is Result.Success)

            val expectedNextDue =
                RecurrenceEngine.calculateNextOccurrence(anchor, anchor, RecurrenceRule.Daily)
            val spawned = h.tasks.all().single { it.id != original.id && it.parentTaskId == null }

            assertFalse(spawned.isCompleted)
            assertEquals(expectedNextDue, spawned.dueDate)
            assertNull(
                "occurrence must not gain a reminder the user never set",
                spawned.reminderTime
            )
            assertEquals("anchor must be preserved across occurrences", anchor, spawned.recurrenceAnchor)
            assertTrue("no reminder may be scheduled for a reminder-less series", h.reminders.scheduled.isEmpty())
            assertTrue(h.reminders.cancelled.contains(original.id))
        }
}
