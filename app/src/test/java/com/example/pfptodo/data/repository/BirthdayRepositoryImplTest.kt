package com.example.pfptodo.data.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.data.local.dao.BirthdayDao
import com.example.pfptodo.data.local.entity.BirthdayEntity
import com.example.pfptodo.domain.model.Birthday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeBirthdayDao : BirthdayDao {
    val rows = linkedMapOf<Long, BirthdayEntity>()
    private var nextId = 1L
    var getByContactIdCalls = 0

    override fun getAllBirthdays(): Flow<List<BirthdayEntity>> = flowOf(rows.values.toList())
    override suspend fun getAllBirthdaysNow(): List<BirthdayEntity> = rows.values.toList()
    override suspend fun getBirthdayById(id: Long): BirthdayEntity? = rows[id]
    override suspend fun getByContactId(contactId: String): BirthdayEntity? {
        getByContactIdCalls++
        return rows.values.firstOrNull { it.contactId == contactId }
    }
    override suspend fun insertBirthday(birthday: BirthdayEntity): Long {
        val id = nextId++
        rows[id] = birthday.copy(id = id)
        return id
    }
    override suspend fun updateBirthday(birthday: BirthdayEntity) {
        rows[birthday.id] = birthday
    }
    override suspend fun deleteBirthday(birthday: BirthdayEntity) {
        rows.remove(birthday.id)
    }
    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }
}

/**
 * M-18(b): inserting a new birthday whose contactId already has a birthday must
 * refresh the existing row instead of creating a duplicate.
 */
class BirthdayRepositoryImplTest {

    private lateinit var dao: FakeBirthdayDao
    private lateinit var repository: BirthdayRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeBirthdayDao()
        repository = BirthdayRepositoryImpl(dao)
    }

    @Test
    fun `first birthday for a contact inserts a new row`() = runBlocking {
        val result = repository.insertBirthday(Birthday(name = "Sara", month = 5, day = 6, contactId = "c1"))
        assertTrue(result is Result.Success)
        assertEquals(1, dao.rows.size)
        assertEquals("Sara", dao.rows.values.single().name)
    }

    @Test
    fun `second birthday for the same contact updates the existing row`() = runBlocking {
        val first = repository.insertBirthday(
            Birthday(name = "Sara", month = 5, day = 6, contactId = "c1")
        )
        val firstId = (first as Result.Success).data
        val createdAt = dao.rows.getValue(firstId).createdAt

        val second = repository.insertBirthday(
            Birthday(name = "Sara Ahmadi", month = 7, day = 8, birthYear = 1995, contactId = "c1")
        )

        assertTrue(second is Result.Success)
        assertEquals(firstId, (second as Result.Success).data)
        assertEquals(1, dao.rows.size)
        val row = dao.rows.getValue(firstId)
        assertEquals("Sara Ahmadi", row.name)
        assertEquals(7, row.month)
        assertEquals(8, row.day)
        assertEquals(1995, row.birthYear)
        // Refresh keeps the original row identity, including its createdAt.
        assertEquals(createdAt, row.createdAt)
    }

    @Test
    fun `birthday without a contactId never dedupes`() = runBlocking {
        repository.insertBirthday(Birthday(name = "Sara", month = 5, day = 6))
        repository.insertBirthday(Birthday(name = "Sara", month = 5, day = 6))
        assertEquals(2, dao.rows.size)
        assertEquals(0, dao.getByContactIdCalls)
    }

    @Test
    fun `different contacts get separate rows`() = runBlocking {
        repository.insertBirthday(Birthday(name = "Sara", month = 5, day = 6, contactId = "c1"))
        repository.insertBirthday(Birthday(name = "Ali", month = 1, day = 2, contactId = "c2"))
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `updating an existing id skips the contact lookup entirely`() = runBlocking {
        val id = (repository.insertBirthday(
            Birthday(name = "Sara", month = 5, day = 6, contactId = "c1")
        ) as Result.Success).data
        val callsBefore = dao.getByContactIdCalls

        // A record that already has an id (an edit being saved through update,
        // or any non-new insert) must not go through the dedupe path.
        val edit = repository.insertBirthday(Birthday(id = id, name = "Sara", month = 9, day = 9, contactId = "c1"))
        assertTrue(edit is Result.Success)
        assertEquals(callsBefore, dao.getByContactIdCalls)
    }
}
