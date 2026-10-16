package com.example.pfptodo.domain.usecase.tag

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow

/**
 * Wrapper class containing all tag-related use cases.
 */
data class TagUseCases(
    val getAllTags: GetAllTags,
    val getTagById: GetTagById,
    val getTagByName: GetTagByName,
    val addTag: AddTag,
    val updateTag: UpdateTag,
    val deleteTag: DeleteTag,
    val getTagsForTask: GetTagsForTask,
    val setTaskTags: SetTaskTags
)

/**
 * Retrieves a flow of all available tags.
 */
class GetAllTags(private val repository: TagRepository) {
    operator fun invoke(): Flow<List<Tag>> = repository.getAllTags()
}

/**
 * Retrieves a single tag by its unique ID.
 */
class GetTagById(private val repository: TagRepository) {
    suspend operator fun invoke(id: Long): Result<Tag?> = repository.getTagById(id)
}

/**
 * Retrieves a single tag by its name.
 */
class GetTagByName(private val repository: TagRepository) {
    suspend operator fun invoke(name: String): Result<Tag?> = repository.getTagByName(name.trim())
}

/**
 * Adds a new tag after validating name presence and uniqueness.
 */
class AddTag(private val repository: TagRepository) {
    suspend operator fun invoke(tag: Tag): Result<Long> {
        val trimmedName = tag.name.trim()
        if (trimmedName.isBlank()) {
            return Result.Error(IllegalArgumentException("Tag name cannot be empty"))
        }

        val existing = repository.getTagByName(trimmedName)
        if (existing is Result.Success && existing.data != null) {
            return Result.Error(IllegalArgumentException("A tag with name '$trimmedName' already exists"))
        }

        return repository.insertTag(tag.copy(name = trimmedName))
    }
}

/**
 * Updates an existing tag after validating name presence and uniqueness.
 */
class UpdateTag(private val repository: TagRepository) {
    suspend operator fun invoke(tag: Tag): Result<Unit> {
        val trimmedName = tag.name.trim()
        if (trimmedName.isBlank()) {
            return Result.Error(IllegalArgumentException("Tag name cannot be empty"))
        }

        val existing = repository.getTagByName(trimmedName)
        if (existing is Result.Success && existing.data != null && existing.data!!.id != tag.id) {
            return Result.Error(IllegalArgumentException("A tag with name '$trimmedName' already exists"))
        }

        return repository.updateTag(tag.copy(name = trimmedName))
    }
}

/**
 * Deletes a tag and cascades removal of all associated task assignments.
 */
class DeleteTag(private val repository: TagRepository) {
    suspend operator fun invoke(tag: Tag): Result<Unit> = repository.deleteTag(tag)
}

/**
 * Retrieves all tags assigned to a specific task.
 */
class GetTagsForTask(private val repository: TagRepository) {
    operator fun invoke(taskId: Long): Flow<List<Tag>> = repository.getTagsForTask(taskId)
}

/**
 * Sets the complete list of tags assigned to a task.
 */
class SetTaskTags(private val repository: TagRepository) {
    suspend operator fun invoke(taskId: Long, tagIds: List<Long>): Result<Unit> =
        repository.setTaskTags(taskId, tagIds)
}
