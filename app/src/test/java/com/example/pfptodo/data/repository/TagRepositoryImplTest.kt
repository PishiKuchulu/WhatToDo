package com.example.pfptodo.data.repository

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.data.local.dao.TagDao
import com.example.pfptodo.data.local.entity.TagEntity
import com.example.pfptodo.data.local.entity.TaskTagCrossRef
import com.example.pfptodo.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTagDao : TagDao {
    val rows = linkedMapOf<Long, TagEntity>()
    private var nextId = 1L
    var insertCalls = 0

    override fun getAllTags(): Flow<List<TagEntity>> = flowOf(rows.values.toList())
    override suspend fun getAllTagsRaw(): List<TagEntity> = rows.values.toList()
    override suspend fun getTagById(id: Long): TagEntity? = rows[id]

    // Mirrors the real query: LOWER(name) = LOWER(:name), no trimming.
    override suspend fun getTagByName(name: String): TagEntity? =
        rows.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun insertTag(tag: TagEntity): Long {
        insertCalls++
        val id = nextId++
        rows[id] = tag.copy(id = id)
        return id
    }

    override suspend fun updateTag(tag: TagEntity) {
        rows[tag.id] = tag
    }

    override suspend fun deleteTag(tag: TagEntity) {
        rows.remove(tag.id)
    }

    override fun getTagsForTask(taskId: Long): Flow<List<TagEntity>> = flowOf(emptyList())
    override suspend fun getTagsForTaskSync(taskId: Long): List<TagEntity> = emptyList()
    override suspend fun insertTaskTagCrossRef(crossRef: TaskTagCrossRef) = Unit
    override suspend fun deleteTaskTagCrossRef(taskId: Long, tagId: Long) = Unit
    override suspend fun deleteTagsForTask(taskId: Long) = Unit
    override suspend fun getAllTaskTagCrossRefsRaw(): List<TaskTagCrossRef> = emptyList()
}

/**
 * M-23: tag creation canonicalizes names — the lookup is case-insensitive but
 * the unique index is case-sensitive, so a case-variant must reuse the
 * existing row instead of inserting a near-duplicate.
 */
class TagRepositoryImplTest {

    private lateinit var dao: FakeTagDao
    private lateinit var repository: TagRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeTagDao()
        repository = TagRepositoryImpl(dao)
    }

    @Test
    fun `insert trims the name before storing`() = runBlocking {
        val result = repository.insertTag(Tag(name = "  Work  "))
        assertTrue(result is Result.Success)
        assertEquals("Work", dao.rows.values.single().name)
    }

    @Test
    fun `case variant of an existing tag reuses its row`() = runBlocking {
        val first = repository.insertTag(Tag(name = "Work"))
        val firstId = (first as Result.Success).data

        val variant = repository.insertTag(Tag(name = "work"))
        assertTrue(variant is Result.Success)
        assertEquals(firstId, (variant as Result.Success).data)
        assertEquals(1, dao.rows.size)
        assertEquals(1, dao.insertCalls)
        // The first-created casing stays the canonical stored form.
        assertEquals("Work", dao.rows.values.single().name)
    }

    @Test
    fun `padded variant of an existing tag reuses its row`() = runBlocking {
        val first = repository.insertTag(Tag(name = "Work"))
        val firstId = (first as Result.Success).data

        val variant = repository.insertTag(Tag(name = "  WORK "))
        assertEquals(firstId, (variant as Result.Success).data)
        assertEquals(1, dao.rows.size)
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun `genuinely different names still insert separate rows`() = runBlocking {
        repository.insertTag(Tag(name = "Work"))
        repository.insertTag(Tag(name = "Workshop"))
        assertEquals(2, dao.rows.size)
        assertEquals(2, dao.insertCalls)
    }
}
