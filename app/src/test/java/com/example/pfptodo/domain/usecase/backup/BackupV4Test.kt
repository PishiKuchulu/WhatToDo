package com.example.pfptodo.domain.usecase.backup

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.BirthdayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class InMemoryBirthdayRepository : BirthdayRepository {
    val rows = linkedMapOf<Long, Birthday>()
    private var nextId = 1L
    override fun getAllBirthdays(): Flow<List<Birthday>> = flowOf(rows.values.toList())
    override suspend fun getAllBirthdaysRaw(): List<Birthday> = rows.values.toList()
    override suspend fun getBirthdayById(id: Long) = Result.Success(rows[id])
    override suspend fun getByContactId(contactId: String) =
        Result.Success(rows.values.firstOrNull { it.contactId == contactId })
    override suspend fun insertBirthday(birthday: Birthday): Result<Long> {
        val id = nextId++
        rows[id] = birthday.copy(id = id)
        return Result.Success(id)
    }
    override suspend fun updateBirthday(birthday: Birthday): Result<Unit> {
        rows[birthday.id] = birthday
        return Result.Success(Unit)
    }
    override suspend fun deleteBirthday(birthday: Birthday): Result<Unit> {
        rows.remove(birthday.id)
        return Result.Success(Unit)
    }
    override suspend fun deleteById(id: Long): Result<Unit> {
        rows.remove(id)
        return Result.Success(Unit)
    }
}

class BackupV4Test {

    private lateinit var tasks: InMemoryTaskRepository
    private lateinit var categories: InMemoryCategoryRepository
    private lateinit var tags: InMemoryTagRepository
    private lateinit var attachments: InMemoryAttachmentRepository
    private lateinit var birthdays: InMemoryBirthdayRepository
    private lateinit var maintenance: RecordingMaintenance
    private lateinit var useCases: BackupUseCases

    @Before
    fun setUp() {
        tasks = InMemoryTaskRepository()
        categories = InMemoryCategoryRepository()
        tags = InMemoryTagRepository()
        attachments = InMemoryAttachmentRepository()
        birthdays = InMemoryBirthdayRepository()
        maintenance = RecordingMaintenance()
        useCases = BackupUseCases(
            tasks, categories, attachments, tags,
            SnapshotTransactionRunner(tasks, categories, tags, attachments),
            maintenance, birthdays
        )
    }

    @Test
    fun `birthdays and completedAt survive an export-import round trip`() = runBlocking {
        val completedAt = Instant.parse("2026-03-01T10:15:30Z")
        tasks.insertTask(Task(title = "Done", isCompleted = true, completedAt = completedAt))
        birthdays.insertBirthday(
            Birthday(name = "Sara", month = 2, day = 29, birthYear = 1992, notifyOnDayBefore = true, reminderMinutesOfDay = 600)
        )
        val json = useCases.exportToJson()
        assertTrue(json.contains("\"birthdays\""))

        // Restore into empty repositories.
        val t2 = InMemoryTaskRepository()
        val c2 = InMemoryCategoryRepository()
        val g2 = InMemoryTagRepository()
        val a2 = InMemoryAttachmentRepository()
        val b2 = InMemoryBirthdayRepository()
        val restore = BackupUseCases(t2, c2, a2, g2, SnapshotTransactionRunner(t2, c2, g2, a2), RecordingMaintenance(), b2)
        assertTrue(restore.importFromJson(json) is Result.Success)

        assertEquals(completedAt, t2.getAllTasksRaw().single().completedAt)
        val b = b2.getAllBirthdaysRaw().single()
        assertEquals("Sara", b.name)
        assertEquals(29, b.day)
        assertEquals(1992, b.birthYear)
        assertTrue(b.notifyOnDayBefore)
        assertEquals(600, b.reminderMinutesOfDay)
    }

    @Test
    fun `importing twice does not duplicate birthdays or identical categories`() = runBlocking {
        categories.insertCategory(Category(name = "Work", color = "#123456", iconName = "work"))
        birthdays.insertBirthday(Birthday(name = "Ali", month = 5, day = 6))
        val json = useCases.exportToJson()

        assertTrue(useCases.importFromJson(json) is Result.Success)
        assertEquals(1, birthdays.getAllBirthdaysRaw().size)
        assertEquals(1, categories.getAllCategoriesRaw().size)
    }

    @Test
    fun `legacy file without completedAt falls back to updatedAt for completed tasks`() = runBlocking {
        val json = """
            {"formatVersion": 3, "tasks": [
              {"id":1,"title":"Old","isCompleted":true,"priority":"LOW","recurrenceRule":"NONE",
               "updatedAt":"2026-01-02T03:04:05Z"}
            ]}
        """.trimIndent()
        assertTrue(useCases.importFromJson(json) is Result.Success)
        assertNotNull(tasks.getAllTasksRaw().single().completedAt)
    }

    @Test
    fun `invalid birthday month is rejected`() = runBlocking {
        val json = """{"formatVersion": 4, "birthdays": [{"id":1,"name":"X","month":13,"day":1}]}"""
        assertTrue(useCases.importFromJson(json) is Result.Error)
    }
}
