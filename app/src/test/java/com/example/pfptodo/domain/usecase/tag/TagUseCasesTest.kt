package com.example.pfptodo.domain.usecase.tag

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class TagUseCasesTest {

    private lateinit var fakeRepo: FakeTagRepository
    private lateinit var tagUseCases: TagUseCases

    @Before
    fun setUp() {
        fakeRepo = FakeTagRepository()
        tagUseCases = TagUseCases(
            getAllTags = GetAllTags(fakeRepo),
            getTagById = GetTagById(fakeRepo),
            getTagByName = GetTagByName(fakeRepo),
            addTag = AddTag(fakeRepo),
            updateTag = UpdateTag(fakeRepo),
            deleteTag = DeleteTag(fakeRepo),
            getTagsForTask = GetTagsForTask(fakeRepo),
            setTaskTags = SetTaskTags(fakeRepo)
        )
    }

    @Test
    fun addTag_success_whenUniqueName() = runBlocking {
        val result = tagUseCases.addTag(Tag(name = "Urgent", color = "#E57373"))
        assertTrue("Expected success", result is Result.Success)
        val id = (result as Result.Success).data
        assertTrue(id > 0L)

        val tags = tagUseCases.getAllTags().first()
        assertEquals(1, tags.size)
        assertEquals("Urgent", tags.first().name)
    }

    @Test
    fun addTag_fails_whenDuplicateName_caseInsensitive() = runBlocking {
        tagUseCases.addTag(Tag(name = "Home", color = "#81C784"))

        val duplicateResult = tagUseCases.addTag(Tag(name = "home", color = "#64B5F6"))
        assertTrue("Expected error for duplicate tag name", duplicateResult is Result.Error)
    }

    @Test
    fun addTag_fails_whenBlankName() = runBlocking {
        val result = tagUseCases.addTag(Tag(name = "   "))
        assertTrue("Expected error for blank tag name", result is Result.Error)
    }

    @Test
    fun updateTag_success() = runBlocking {
        val addRes = tagUseCases.addTag(Tag(name = "Work", color = "#64B5F6"))
        val id = (addRes as Result.Success).data

        val updateRes = tagUseCases.updateTag(Tag(id = id, name = "Career", color = "#9575CD"))
        assertTrue("Expected success", updateRes is Result.Success)

        val updated = (tagUseCases.getTagById(id) as Result.Success).data
        assertNotNull(updated)
        assertEquals("Career", updated!!.name)
        assertEquals("#9575CD", updated.color)
    }

    @Test
    fun updateTag_fails_whenCollidesWithAnotherTagName() = runBlocking {
        val res1 = tagUseCases.addTag(Tag(name = "Alpha"))
        val res2 = tagUseCases.addTag(Tag(name = "Beta"))
        val id2 = (res2 as Result.Success).data

        // Trying to rename Beta to Alpha
        val updateRes = tagUseCases.updateTag(Tag(id = id2, name = "alpha"))
        assertTrue("Expected error when renaming to existing tag name", updateRes is Result.Error)
    }

    @Test
    fun deleteTag_removesTagAndAssociations() = runBlocking {
        val res = tagUseCases.addTag(Tag(name = "Temp"))
        val tagId = (res as Result.Success).data

        tagUseCases.setTaskTags(taskId = 100L, listOf(tagId))
        val taskTagsBefore = tagUseCases.getTagsForTask(100L).first()
        assertEquals(1, taskTagsBefore.size)

        val tagToDelete = (tagUseCases.getTagById(tagId) as Result.Success).data!!
        tagUseCases.deleteTag(tagToDelete)

        val tagsAfter = tagUseCases.getAllTags().first()
        assertTrue(tagsAfter.isEmpty())

        val taskTagsAfter = tagUseCases.getTagsForTask(100L).first()
        assertTrue(taskTagsAfter.isEmpty())
    }

    @Test
    fun setTaskTags_associatesMultipleTags() = runBlocking {
        val id1 = (tagUseCases.addTag(Tag(name = "T1")) as Result.Success).data
        val id2 = (tagUseCases.addTag(Tag(name = "T2")) as Result.Success).data

        tagUseCases.setTaskTags(10L, listOf(id1, id2))

        val taskTags = tagUseCases.getTagsForTask(10L).first()
        assertEquals(2, taskTags.size)
        assertTrue(taskTags.any { it.id == id1 })
        assertTrue(taskTags.any { it.id == id2 })
    }
}

class FakeTagRepository : TagRepository {
    private val tagsFlow = MutableStateFlow<List<Tag>>(emptyList())
    private val crossRefs = mutableListOf<Pair<Long, Long>>() // (taskId, tagId)
    private var nextId = 1L

    override fun getAllTags(): Flow<List<Tag>> = tagsFlow

    override suspend fun getAllTagsRaw(): List<Tag> = tagsFlow.value

    override suspend fun getTagById(id: Long): Result<Tag?> {
        return Result.Success(tagsFlow.value.find { it.id == id })
    }

    override suspend fun getTagByName(name: String): Result<Tag?> {
        return Result.Success(tagsFlow.value.find { it.name.equals(name.trim(), ignoreCase = true) })
    }

    override suspend fun insertTag(tag: Tag): Result<Long> {
        val id = if (tag.id > 0L) tag.id else nextId++
        val newTag = tag.copy(id = id)
        tagsFlow.value = tagsFlow.value + newTag
        return Result.Success(id)
    }

    override suspend fun updateTag(tag: Tag): Result<Unit> {
        tagsFlow.value = tagsFlow.value.map { if (it.id == tag.id) tag else it }
        return Result.Success(Unit)
    }

    override suspend fun deleteTag(tag: Tag): Result<Unit> {
        tagsFlow.value = tagsFlow.value.filter { it.id != tag.id }
        crossRefs.removeAll { it.second == tag.id }
        return Result.Success(Unit)
    }

    override fun getTagsForTask(taskId: Long): Flow<List<Tag>> {
        return tagsFlow.map { allTags ->
            val tagIds = crossRefs.filter { it.first == taskId }.map { it.second }.toSet()
            allTags.filter { it.id in tagIds }
        }
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
