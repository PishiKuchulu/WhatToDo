package com.example.pfptodo.domain.usecase.backup

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Attachment
import com.example.pfptodo.domain.model.AttachmentType
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.AttachmentRepository
import com.example.pfptodo.domain.repository.BackupMaintenance
import com.example.pfptodo.domain.repository.CategoryRepository
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the backup import parent/child ordering fix.
 */
class BackupImportOrderingTest {

    private lateinit var tasks: InMemoryTaskRepository
    private lateinit var categories: InMemoryCategoryRepository
    private lateinit var tags: InMemoryTagRepository
    private lateinit var attachments: InMemoryAttachmentRepository
    private lateinit var maintenance: RecordingMaintenance
    private lateinit var useCases: BackupUseCases

    @Before
    fun setUp() {
        tasks = InMemoryTaskRepository()
        categories = InMemoryCategoryRepository()
        tags = InMemoryTagRepository()
        attachments = InMemoryAttachmentRepository()
        maintenance = RecordingMaintenance()
        useCases = BackupUseCases(
            tasks, categories, attachments, tags,
            SnapshotTransactionRunner(tasks, categories, tags, attachments),
            maintenance
        )
    }

    @Test
    fun `import with child before parent in JSON succeeds`() = runBlocking {
        // Create a backup where child comes before parent
        val parent = Task(
            id = 10, title = "Parent", priority = Priority.HIGH,
            dueDate = java.time.Instant.parse("2026-08-10T09:00:00Z"),
            recurrenceRule = RecurrenceRule.None,
            createdAt = java.time.Instant.parse("2026-08-01T00:00:00Z"),
            updatedAt = java.time.Instant.parse("2026-08-01T00:00:00Z")
        )
        val child = Task(
            id = 11, title = "Child", parentTaskId = 10, priority = Priority.LOW,
            createdAt = java.time.Instant.parse("2026-08-01T00:00:00Z"),
            updatedAt = java.time.Instant.parse("2026-08-01T00:00:00Z")
        )
        // JSON order: child FIRST, then parent
        val json = """
            {"formatVersion": 2,
             "categories": [],
             "tasks": [
               {"id":11,"title":"Child","parentTaskId":10,"orderIndex":0,"recurrenceRule":"NONE"},
               {"id":10,"title":"Parent","orderIndex":0,"recurrenceRule":"NONE"}
             ],
             "attachments": []}
        """.trimIndent()

        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Success)

        // Verify both tasks imported
        assertEquals(2, tasks.getAllTasksRaw().size)

        // Verify parent-child relationship preserved
        val importedChild = tasks.getAllTasksRaw().find { it.title == "Child" }
        val importedParent = tasks.getAllTasksRaw().find { it.title == "Parent" }
        assertNotNull(importedChild)
        assertNotNull(importedParent)
        assertEquals(importedParent!!.id, importedChild!!.parentTaskId)
    }

    @Test
    fun `import with multiple levels of nesting`() = runBlocking {
        // Grandparent -> Parent -> Child
        val json = """
            {"formatVersion": 2,
             "categories": [],
             "tasks": [
               {"id":3,"title":"Grandchild","parentTaskId":2,"orderIndex":0,"recurrenceRule":"NONE"},
               {"id":1,"title":"Grandparent","orderIndex":0,"recurrenceRule":"NONE"},
               {"id":2,"title":"Parent","parentTaskId":1,"orderIndex":0,"recurrenceRule":"NONE"}
             ],
             "attachments": []}
        """.trimIndent()

        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Success)
        assertEquals(3, tasks.getAllTasksRaw().size)

        // Verify chain
        val gp = tasks.getAllTasksRaw().find { it.title == "Grandparent" }!!
        val p = tasks.getAllTasksRaw().find { it.title == "Parent" }!!
        val c = tasks.getAllTasksRaw().find { it.title == "Grandchild" }!!

        assertEquals(gp.id, p.parentTaskId)
        assertEquals(p.id, c.parentTaskId)
    }

    @Test
    fun `import with circular parent reference fails validation`() = runBlocking {
        val json = """
            {"formatVersion": 2,
             "categories": [],
             "tasks": [
               {"id":1,"title":"A","parentTaskId":2,"orderIndex":0,"recurrenceRule":"NONE"},
               {"id":2,"title":"B","parentTaskId":1,"orderIndex":0,"recurrenceRule":"NONE"}
             ],
             "attachments": []}
        """.trimIndent()

        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Error)
    }
}