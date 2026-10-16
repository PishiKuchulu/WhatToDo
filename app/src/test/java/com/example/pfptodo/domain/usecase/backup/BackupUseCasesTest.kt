package com.example.pfptodo.domain.usecase.backup

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Attachment
import com.example.pfptodo.domain.model.AttachmentType
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.AttachmentRepository
import com.example.pfptodo.domain.repository.BackupMaintenance
import com.example.pfptodo.domain.repository.CategoryRepository
import com.example.pfptodo.domain.repository.TagRepository
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

// ============================ In-memory repositories ============================

open class InMemoryTaskRepository : TaskRepository {
    val rows = linkedMapOf<Long, Task>()
    private var nextId = 1L

    override fun getAllMainTasks(): Flow<List<Task>> = flowOf(rows.values.filter { it.parentTaskId == null })
    override fun searchTasks(query: String): Flow<List<Task>> = flowOf(rows.values.toList())
    override suspend fun getTaskById(id: Long) = Result.Success(rows[id])
    override fun observeTaskById(id: Long): Flow<Task?> = flowOf(rows[id])
    override fun getSubtasksForTask(parentId: Long): Flow<List<Task>> = flowOf(rows.values.filter { it.parentTaskId == parentId })
    override suspend fun getAllTasksRaw(): List<Task> = rows.values.toList()

    override suspend fun insertTask(task: Task): Result<Long> {
        val id = nextId++
        rows[id] = task.copy(id = id)
        return Result.Success(id)
    }

    override suspend fun updateTask(task: Task): Result<Unit> {
        rows[task.id] = task
        return Result.Success(Unit)
    }

    override suspend fun deleteTask(task: Task): Result<Unit> {
        rows.remove(task.id)
        rows.keys.filter { rows[it]?.parentTaskId == task.id }.forEach { rows.remove(it) }
        return Result.Success(Unit)
    }

    override suspend fun updateTaskOrder(id: Long, orderIndex: Int): Result<Unit> {
        rows[id]?.let { rows[id] = it.copy(orderIndex = orderIndex) }
        return Result.Success(Unit)
    }

    override suspend fun swapTaskOrder(idA: Long, idB: Long): Result<Unit> {
        val a = rows[idA]?.orderIndex ?: return Result.Success(Unit)
        val b = rows[idB]?.orderIndex ?: return Result.Success(Unit)
        rows[idA] = rows[idA]!!.copy(orderIndex = b)
        rows[idB] = rows[idB]!!.copy(orderIndex = a)
        return Result.Success(Unit)
    }
}

class InMemoryCategoryRepository : CategoryRepository {
    val rows = linkedMapOf<Long, Category>()
    private var nextId = 1L

    override fun getAllCategories(): Flow<List<Category>> = flowOf(rows.values.toList())
    override suspend fun getAllCategoriesRaw(): List<Category> = rows.values.toList()
    override suspend fun getCategoryById(id: Long): Result<Category?> = Result.Success(rows[id])
    override suspend fun insertCategory(category: Category): Result<Long> {
        val id = nextId++
        rows[id] = category.copy(id = id)
        return Result.Success(id)
    }

    override suspend fun updateCategory(category: Category): Result<Unit> {
        rows[category.id] = category
        return Result.Success(Unit)
    }

    override suspend fun deleteCategory(category: Category): Result<Unit> {
        rows.remove(category.id)
        return Result.Success(Unit)
    }
}

class InMemoryTagRepository : TagRepository {
    val rows = linkedMapOf<Long, Tag>()
    val crossRefs = mutableListOf<Pair<Long, Long>>() // (taskId, tagId)
    private var nextId = 1L

    override fun getAllTags(): Flow<List<Tag>> = flowOf(rows.values.toList())
    override suspend fun getAllTagsRaw(): List<Tag> = rows.values.toList()
    override suspend fun getTagById(id: Long): Result<Tag?> = Result.Success(rows[id])
    override suspend fun getTagByName(name: String): Result<Tag?> {
        return Result.Success(rows.values.find { it.name.equals(name.trim(), ignoreCase = true) })
    }

    override suspend fun insertTag(tag: Tag): Result<Long> {
        val id = if (tag.id > 0L) tag.id else nextId++
        rows[id] = tag.copy(id = id)
        return Result.Success(id)
    }

    override suspend fun updateTag(tag: Tag): Result<Unit> {
        rows[tag.id] = tag
        return Result.Success(Unit)
    }

    override suspend fun deleteTag(tag: Tag): Result<Unit> {
        rows.remove(tag.id)
        crossRefs.removeAll { it.second == tag.id }
        return Result.Success(Unit)
    }

    override fun getTagsForTask(taskId: Long): Flow<List<Tag>> {
        val tagIds = crossRefs.filter { it.first == taskId }.map { it.second }.toSet()
        return flowOf(rows.values.filter { it.id in tagIds })
    }

    override suspend fun setTaskTags(taskId: Long, tagIds: List<Long>): Result<Unit> {
        crossRefs.removeAll { it.first == taskId }
        tagIds.forEach { crossRefs.add(taskId to it) }
        return Result.Success(Unit)
    }

    override suspend fun insertTaskTagCrossRef(taskId: Long, tagId: Long): Result<Unit> {
        if (!crossRefs.contains(taskId to tagId)) {
            crossRefs.add(taskId to tagId)
        }
        return Result.Success(Unit)
    }

    override suspend fun getAllTaskTagCrossRefsRaw(): List<Pair<Long, Long>> {
        return crossRefs.toList()
    }
}

open class InMemoryAttachmentRepository : AttachmentRepository {
    val rows = linkedMapOf<Long, Attachment>()
    private var nextId = 1L

    override fun getAttachmentsForTask(taskId: Long): Flow<List<Attachment>> = flowOf(rows.values.filter { it.taskId == taskId })
    override suspend fun getAllAttachmentsRaw(): List<Attachment> = rows.values.toList()
    override suspend fun insertAttachment(attachment: Attachment): Result<Long> {
        val id = nextId++
        rows[id] = attachment.copy(id = id)
        return Result.Success(id)
    }

    override suspend fun deleteAttachment(attachment: Attachment): Result<Unit> {
        rows.remove(attachment.id)
        return Result.Success(Unit)
    }
}

/** Fails on insert #failOnCall (1-based). */
class FailingTaskRepository(private val failOnCall: Int) : InMemoryTaskRepository() {
    private var insertCalls = 0
    override suspend fun insertTask(task: Task): Result<Long> {
        insertCalls++
        if (insertCalls == failOnCall) throw IllegalStateException("simulated insert failure")
        return super.insertTask(task)
    }
}

class FailingAttachmentRepository : InMemoryAttachmentRepository() {
    override suspend fun insertAttachment(attachment: Attachment): Result<Long> {
        return Result.Error(IllegalStateException("simulated attachment insert failure"))
    }
}

/** Snapshot/rollback emulation of a real database transaction. */
class SnapshotTransactionRunner(
    private val tasks: InMemoryTaskRepository,
    private val categories: InMemoryCategoryRepository,
    private val tags: InMemoryTagRepository,
    private val attachments: InMemoryAttachmentRepository
) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val taskSnapshot = tasks.rows.toMap()
        val categorySnapshot = categories.rows.toMap()
        val tagSnapshot = tags.rows.toMap()
        val crossRefSnapshot = tags.crossRefs.toList()
        val attachmentSnapshot = attachments.rows.toMap()
        return try {
            block()
        } catch (e: Exception) {
            tasks.rows.clear(); tasks.rows.putAll(taskSnapshot)
            categories.rows.clear(); categories.rows.putAll(categorySnapshot)
            tags.rows.clear(); tags.rows.putAll(tagSnapshot)
            tags.crossRefs.clear(); tags.crossRefs.addAll(crossRefSnapshot)
            attachments.rows.clear(); attachments.rows.putAll(attachmentSnapshot)
            throw e
        }
    }
}

class RecordingMaintenance : BackupMaintenance {
    var resetSeqCalls = 0
    var lastTables: List<String> = emptyList()

    override suspend fun resetSequences(tables: List<String>) {
        resetSeqCalls++
        lastTables = tables
    }
}

// ============================ Tests ============================

class BackupUseCasesTest {

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

    /** Seeds one category, one tag, one parent task with tag, one subtask; returns the parent task with real ids. */
    private fun seed(): Pair<Task, Category> {
        return runBlocking {
            val catId = categories.insertCategory(Category(name = "Work", color = "#123456", iconName = "work")).unwrap2()
            val tagId = tags.insertTag(Tag(name = "Important", color = "#E57373")).unwrap2()
            val parentId = tasks.insertTask(
                Task(
                    title = "Parent", priority = Priority.HIGH, categoryId = catId,
                    dueDate = java.time.Instant.parse("2026-08-10T09:00:00Z"),
                    reminderTime = java.time.Instant.parse("2026-08-10T08:30:00Z"),
                    recurrenceRule = RecurrenceRule.Weekly,
                    createdAt = java.time.Instant.parse("2026-08-01T00:00:00Z"),
                    updatedAt = java.time.Instant.parse("2026-08-01T00:00:00Z")
                )
            ).unwrap2()
            tags.insertTaskTagCrossRef(parentId, tagId)
            tasks.insertTask(
                Task(
                    title = "Subtask", parentTaskId = parentId, categoryId = catId,
                    createdAt = java.time.Instant.parse("2026-08-01T00:00:00Z"),
                    updatedAt = java.time.Instant.parse("2026-08-01T00:00:00Z")
                )
            )
            val parent = tasks.rows.getValue(parentId)
            Pair(parent, categories.rows.getValue(catId))
        }
    }

    // ---- Export ----

    @Test
    fun `export contains versioned envelope v4 and tags`() = runBlocking {
        seed()
        val json = useCases.exportToJson()
        assertTrue(json.contains("\"formatVersion\": 4"))
        assertTrue(json.contains("\"exportedAt\""))
        assertTrue(json.contains("\"Work\""))
        assertTrue(json.contains("\"Important\""))
        assertTrue(json.contains("\"Parent\""))
        assertTrue(json.contains("\"Subtask\""))
        assertTrue(json.contains("\"taskTagCrossRefs\""))
    }

    // ---- Round trip ----

    @Test
    fun `round trip into empty database remaps ids and preserves tag relationships`() = runBlocking {
        seed()
        val json = useCases.exportToJson()

        tasks = InMemoryTaskRepository()
        categories = InMemoryCategoryRepository()
        tags = InMemoryTagRepository()
        attachments = InMemoryAttachmentRepository()
        useCases = BackupUseCases(
            tasks, categories, attachments, tags,
            SnapshotTransactionRunner(tasks, categories, tags, attachments),
            maintenance
        )

        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Success)

        val importedTasks = tasks.getAllTasksRaw()
        assertEquals(2, importedTasks.size)
        val importedParent = importedTasks.first { it.title == "Parent" }
        val importedSub = importedTasks.first { it.title == "Subtask" }

        // parent/child relationship preserved
        assertEquals(importedParent.id, importedSub.parentTaskId)
        // category relationship preserved
        val importedCat = categories.getAllCategoriesRaw().single()
        assertEquals(importedCat.id, importedParent.categoryId)
        assertEquals(importedCat.id, importedSub.categoryId)

        // tag relationship preserved
        val importedTags = tags.getAllTagsRaw()
        assertEquals(1, importedTags.size)
        assertEquals("Important", importedTags.first().name)
        val crossRefs = tags.getAllTaskTagCrossRefsRaw()
        assertEquals(1, crossRefs.size)
        assertEquals(importedParent.id, crossRefs.first().first)
        assertEquals(importedTags.first().id, crossRefs.first().second)

        // sequence maintenance ran with all 4 tables
        assertEquals(1, maintenance.resetSeqCalls)
        assertEquals(listOf("categories", "tags", "tasks", "attachments"), maintenance.lastTables)
    }

    @Test
    fun `import merges existing tag by name without duplicating tag`() = runBlocking {
        seed()
        val json = useCases.exportToJson()

        // New DB has a tag named "important" with a different color already
        tasks = InMemoryTaskRepository()
        categories = InMemoryCategoryRepository()
        tags = InMemoryTagRepository()
        attachments = InMemoryAttachmentRepository()
        useCases = BackupUseCases(
            tasks, categories, attachments, tags,
            SnapshotTransactionRunner(tasks, categories, tags, attachments),
            maintenance
        )

        val existingTagId = tags.insertTag(Tag(name = "important", color = "#64B5F6")).unwrap2()

        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Success)

        // Tag should not be duplicated
        val allTags = tags.getAllTagsRaw()
        assertEquals(1, allTags.size)
        assertEquals(existingTagId, allTags.first().id)

        // Task cross-ref should point to existing tag
        val importedParent = tasks.getAllTasksRaw().first { it.title == "Parent" }
        val crossRefs = tags.getAllTaskTagCrossRefsRaw()
        assertEquals(1, crossRefs.size)
        assertEquals(importedParent.id, crossRefs.first().first)
        assertEquals(existingTagId, crossRefs.first().second)
    }

    @Test
    fun `legacy v1 backup without envelope still imports`() = runBlocking {
        val json = """
            {"categories": [{"id":1,"name":"LegacyCat","color":"#FFF","iconName":"x"}],
             "tasks": [{"id":1,"title":"Old","priority":"LOW","categoryId":1,"orderIndex":0,"recurrenceRule":"NONE"}],
             "attachments": []}
        """.trimIndent()
        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Success)
        val importedTask = tasks.getAllTasksRaw().single()
        assertEquals("Old", importedTask.title)
        val cat = categories.getAllCategoriesRaw().single()
        assertEquals(cat.id, importedTask.categoryId)
    }

    @Test
    fun `legacy v2 backup with formatVersion 2 still imports`() = runBlocking {
        val json = """
            {"formatVersion": 2, "exportedAt": "2026-08-18T10:00:00Z",
             "categories": [{"id":1,"name":"V2Cat","color":"#FFF","iconName":"x"}],
             "tasks": [{"id":1,"title":"V2Task","priority":"HIGH","categoryId":1,"orderIndex":0,"recurrenceRule":"NONE"}],
             "attachments": []}
        """.trimIndent()
        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Success)
        val importedTask = tasks.getAllTasksRaw().single()
        assertEquals("V2Task", importedTask.title)
    }

    @Test
    fun `missing crossRef tag reference fails validation`() = runBlocking {
        val json = """
            {"formatVersion": 3,
             "categories": [],
             "tags": [{"id": 1, "name": "ValidTag"}],
             "tasks": [{"id": 10, "title": "Task 10", "orderIndex": 0, "recurrenceRule": "NONE"}],
             "taskTagCrossRefs": [{"taskId": 10, "tagId": 99}],
             "attachments": []}
        """.trimIndent()
        val result = useCases.importFromJson(json)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception.message!!.contains("missing tag"))
    }

    @Test
    fun `attachment insert failure rolls back entire transaction`() = runBlocking {
        val failingAttachments = FailingAttachmentRepository()
        val rollbackRunner = SnapshotTransactionRunner(tasks, categories, tags, failingAttachments)
        val customUseCases = BackupUseCases(tasks, categories, failingAttachments, tags, rollbackRunner, maintenance)

        val json = """
            {"formatVersion": 3,
             "categories": [{"id": 1, "name": "Cat1", "color": "#F00", "iconName": "x"}],
             "tags": [],
             "tasks": [{"id": 10, "title": "Task with Attachment", "orderIndex": 0, "recurrenceRule": "NONE"}],
             "taskTagCrossRefs": [],
             "attachments": [{"id": 100, "taskId": 10, "type": "IMAGE", "uri": "content://img", "fileName": "photo.jpg"}]}
        """.trimIndent()

        val result = customUseCases.importFromJson(json)
        assertTrue(result is Result.Error)
        assertEquals(0, tasks.getAllTasksRaw().size)
        assertEquals(0, categories.getAllCategoriesRaw().size)
    }
}

/** Unwraps a successful Result<Long> for test seeding. */
private fun Result<Long>.unwrap2(): Long = (this as Result.Success).data
