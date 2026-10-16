package com.example.pfptodo.domain.usecase.backup

import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.*
import com.example.pfptodo.domain.repository.AttachmentRepository
import com.example.pfptodo.domain.repository.BackupMaintenance
import com.example.pfptodo.domain.repository.BirthdayRepository
import com.example.pfptodo.domain.repository.CategoryRepository
import com.example.pfptodo.domain.repository.TagRepository
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.repository.TransactionRunner
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

/**
 * Versioned, validated, transactional backup export/import.
 *
 * Format (v4 — adds `birthdays` and task `completedAt`; v1–v3 files still import):
 * {
 *   "formatVersion": 4,
 *   "exportedAt": "<ISO-8601>",
 *   "categories": [{ "id", "name", "color", "iconName" }],
 *   "tags": [{ "id", "name", "color", "createdAt" }],
 *   "birthdays": [{ "id", "name", "month", "day", "birthYear", "notes",
 *                   "reminderMinutesOfDay", "notifyOnDayBefore", "contactId", "createdAt" }],
 *   "tasks": [{ "id", "title", "description", "notes", "isCompleted", "completedAt", "priority",
 *               "categoryId", "parentTaskId", "dueDate", "reminderTime",
 *               "orderIndex", "recurrenceRule", "recurrenceAnchor", "createdAt", "updatedAt" }],
 *   "taskTagCrossRefs": [{ "taskId", "tagId" }],
 *   "attachments": [{ "id", "taskId", "type", "uri", "fileName" }]
 * }
 *
 * v1 & v2 (legacy, still imported): backwards-compatible parsing.
 */
class BackupUseCases(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val attachmentRepository: AttachmentRepository,
    private val tagRepository: TagRepository,
    private val transactionRunner: TransactionRunner,
    private val backupMaintenance: BackupMaintenance,
    private val birthdayRepository: BirthdayRepository? = null
) {

    companion object {
        const val CURRENT_FORMAT_VERSION = 4L
        const val MIN_SUPPORTED_FORMAT_VERSION = 1L
        // Legacy (v1) backups carried no formatVersion field.
        const val LEGACY_FORMAT_VERSION = 1L

        private const val KEY_FORMAT_VERSION = "formatVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_TAGS = "tags"
        private const val KEY_TASKS = "tasks"
        private const val KEY_TASK_TAG_CROSS_REFS = "taskTagCrossRefs"
        private const val KEY_ATTACHMENTS = "attachments"
        private const val KEY_BIRTHDAYS = "birthdays"
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Exports all tasks, categories, tags, task-tag relations, and attachments to a versioned JSON string.
     */
    suspend fun exportToJson(): String {
        val tasks = taskRepository.getAllTasksRaw()
        val categories = categoryRepository.getAllCategoriesRaw()
        val attachments = attachmentRepository.getAllAttachmentsRaw()
        val tags = tagRepository.getAllTagsRaw()
        val crossRefs = tagRepository.getAllTaskTagCrossRefsRaw()
        val birthdays = birthdayRepository?.getAllBirthdaysRaw() ?: emptyList()

        val root = JsonObject()
        root.addProperty(KEY_FORMAT_VERSION, CURRENT_FORMAT_VERSION)
        root.addProperty(KEY_EXPORTED_AT, Instant.now().toString())

        root.add(KEY_CATEGORIES, categories.mapToJson { c -> serializeCategory(c) })
        root.add(KEY_TAGS, tags.mapToJson { t -> serializeTag(t) })
        root.add(KEY_TASKS, tasks.mapToJson { t -> serializeTask(t) })
        root.add(KEY_TASK_TAG_CROSS_REFS, crossRefs.mapToJson { (taskId, tagId) ->
            JsonObject().apply {
                addProperty("taskId", taskId)
                addProperty("tagId", tagId)
            }
        })
        root.add(KEY_ATTACHMENTS, attachments.mapToJson { a -> serializeAttachment(a) })
        root.add(KEY_BIRTHDAYS, birthdays.mapToJson { b -> serializeBirthday(b) })

        return gson.toJson(root)
    }

    private fun <T> List<T>.mapToJson(serializer: (T) -> JsonObject): JsonArray {
        val array = JsonArray()
        forEach { array.add(serializer(it)) }
        return array
    }

    private fun serializeCategory(category: Category): JsonObject {
        return JsonObject().apply {
            addProperty("id", category.id)
            addProperty("name", category.name)
            addProperty("color", category.color)
            addProperty("iconName", category.iconName)
        }
    }

    private fun serializeTag(tag: Tag): JsonObject {
        return JsonObject().apply {
            addProperty("id", tag.id)
            addProperty("name", tag.name)
            addNullable("color", tag.color)
            addProperty("createdAt", tag.createdAt.toString())
        }
    }

    private fun serializeTask(task: Task): JsonObject {
        return JsonObject().apply {
            addProperty("id", task.id)
            addProperty("title", task.title)
            addNullable("description", task.description)
            addNullable("notes", task.notes)
            addProperty("isCompleted", task.isCompleted)
            addNullable("completedAt", task.completedAt?.toString())
            addProperty("priority", task.priority.name)
            addNullable("categoryId", task.categoryId)
            addNullable("parentTaskId", task.parentTaskId)
            addNullable("dueDate", task.dueDate?.toString())
            addNullable("reminderTime", task.reminderTime?.toString())
            addProperty("orderIndex", task.orderIndex)
            addProperty("recurrenceRule", task.recurrenceRule.toString())
            addNullable("recurrenceAnchor", task.recurrenceAnchor?.toString())
            addProperty("createdAt", task.createdAt.toString())
            addProperty("updatedAt", task.updatedAt.toString())
        }
    }

    private fun serializeBirthday(birthday: Birthday): JsonObject {
        return JsonObject().apply {
            addProperty("id", birthday.id)
            addProperty("name", birthday.name)
            addProperty("month", birthday.month)
            addProperty("day", birthday.day)
            addNullable("birthYear", birthday.birthYear)
            addNullable("notes", birthday.notes)
            addProperty("reminderMinutesOfDay", birthday.reminderMinutesOfDay)
            addProperty("notifyOnDayBefore", birthday.notifyOnDayBefore)
            addNullable("contactId", birthday.contactId)
            addProperty("createdAt", birthday.createdAt)
        }
    }

    private fun serializeAttachment(attachment: Attachment): JsonObject {
        return JsonObject().apply {
            addProperty("id", attachment.id)
            addProperty("taskId", attachment.taskId)
            addProperty("type", attachment.type.name)
            addProperty("uri", attachment.uri)
            addProperty("fileName", attachment.fileName)
        }
    }

    private fun JsonObject.addNullable(name: String, value: Any?) {
        if (value != null) addProperty(name, value.toString()) else add(name, com.google.gson.JsonNull.INSTANCE)
    }

    /**
     * Parsed, validated backup payload. Everything is materialized in memory
     * BEFORE any database write.
     */
    internal data class ParsedBackup(
        val categories: List<Category>,
        val tags: List<Tag>,
        val tasks: List<Task>,
        val taskTagCrossRefs: List<Pair<Long, Long>>,
        val attachments: List<Attachment>,
        val birthdays: List<Birthday> = emptyList()
    )

    internal class BackupFormatException(message: String) : IllegalArgumentException(message)

    /**
     * Import a backup.
     *
     * Policy: SAFE MERGE — imported entities always receive new auto-generated
     * ids (never overwriting existing user data); parent/category/tag/attachment
     * references are remapped to the new ids. The entire import runs inside a
     * single database transaction; any failure rolls everything back.
     *
     * @return [Result.Success] on completed import, [Result.Error] with a
     *         descriptive exception on any validation or persistence failure.
     */
    suspend fun importFromJson(json: String): Result<Unit> {
        val parsed = try {
            parseAndValidate(json)
        } catch (e: BackupFormatException) {
            return Result.Error(e)
        } catch (e: Exception) {
            return Result.Error(BackupFormatException("Invalid backup: ${e.message}"))
        }

        return try {
            transactionRunner.inTransaction {
                importParsed(parsed)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    internal suspend fun parseAndValidate(json: String): ParsedBackup {
        val root: JsonObject = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw BackupFormatException("File is not valid JSON")
        }

        val formatVersion = root.get(KEY_FORMAT_VERSION)?.asLong ?: LEGACY_FORMAT_VERSION
        if (formatVersion !in MIN_SUPPORTED_FORMAT_VERSION..CURRENT_FORMAT_VERSION) {
            throw BackupFormatException("Unsupported backup format version: $formatVersion")
        }

        val categories = parseCategories(root, formatVersion)
        val tags = parseTags(root, formatVersion)
        val tasks = parseTasks(root, formatVersion)
        val taskTagCrossRefs = parseTaskTagCrossRefs(root, formatVersion)
        val attachments = parseAttachments(root, formatVersion)
        val birthdays = parseBirthdays(root)

        validateReferences(tasks, attachments, categories, tags, taskTagCrossRefs)

        return ParsedBackup(categories, tags, tasks, taskTagCrossRefs, attachments, birthdays)
    }

    private fun parseCategories(root: JsonObject, version: Long): List<Category> {
        val array = root.getAsJsonArray(KEY_CATEGORIES) ?: JsonArray()
        return array.mapIndexed { index, el ->
            val obj = objAsObject(el, "categories[$index]")
            Category(
                id = longField(obj, "id", "categories[$index]", default = 0L),
                name = stringField(obj, "name", "categories[$index]").takeIf { it.isNotBlank() }
                    ?: throw BackupFormatException("categories[$index].name is empty"),
                color = stringField(obj, "color", "categories[$index]"),
                iconName = stringField(obj, "iconName", "categories[$index]")
            )
        }
    }

    private fun parseTags(root: JsonObject, version: Long): List<Tag> {
        val array = root.getAsJsonArray(KEY_TAGS) ?: JsonArray()
        return array.mapIndexed { index, el ->
            val obj = objAsObject(el, "tags[$index]")
            Tag(
                id = longField(obj, "id", "tags[$index]", default = 0L),
                name = stringField(obj, "name", "tags[$index]").trim().takeIf { it.isNotBlank() }
                    ?: throw BackupFormatException("tags[$index].name is empty"),
                color = nullableString(obj, "color"),
                createdAt = nullableInstant(obj, "createdAt", "tags[$index]") ?: Instant.now()
            )
        }
    }

    private fun parseTasks(root: JsonObject, version: Long): List<Task> {
        val array = root.getAsJsonArray(KEY_TASKS) ?: JsonArray()
        return array.mapIndexed { index, el ->
            val obj = objAsObject(el, "tasks[$index]")
            val id = longField(obj, "id", "tasks[$index]", default = 0L)

            val priority = try {
                Priority.valueOf(obj.get("priority")?.asString ?: "MEDIUM")
            } catch (e: IllegalArgumentException) {
                throw BackupFormatException("tasks[$index].priority contains unknown value")
            }

            val isCompleted = obj.get("isCompleted")?.asBoolean ?: false
            val updatedAt = nullableInstant(obj, "updatedAt", "tasks[$index]") ?: Instant.now()
            Task(
                id = id,
                title = stringField(obj, "title", "tasks[$index]").takeIf { it.isNotBlank() }
                    ?: throw BackupFormatException("tasks[$index].title is empty"),
                description = nullableString(obj, "description"),
                notes = nullableString(obj, "notes"),
                isCompleted = isCompleted,
                // v1–v3 files carry no completedAt: fall back to the last update, exactly like
                // the database migration does, so statistics keep working after a restore.
                completedAt = nullableInstant(obj, "completedAt", "tasks[$index]")
                    ?: if (isCompleted) updatedAt else null,
                priority = priority,
                categoryId = nullableLong(obj, "categoryId"),
                parentTaskId = nullableLong(obj, "parentTaskId"),
                dueDate = nullableInstant(obj, "dueDate", "tasks[$index]"),
                reminderTime = nullableInstant(obj, "reminderTime", "tasks[$index]"),
                orderIndex = obj.get("orderIndex")?.asInt ?: 0,
                recurrenceRule = RecurrenceRule.fromString(obj.get("recurrenceRule")?.asString ?: "NONE"),
                recurrenceAnchor = nullableInstant(obj, "recurrenceAnchor", "tasks[$index]"),
                createdAt = nullableInstant(obj, "createdAt", "tasks[$index]") ?: Instant.now(),
                updatedAt = updatedAt
            )
        }
    }

    private fun parseBirthdays(root: JsonObject): List<Birthday> {
        val array = root.getAsJsonArray(KEY_BIRTHDAYS) ?: JsonArray()
        return array.mapIndexed { index, el ->
            val path = "birthdays[$index]"
            val obj = objAsObject(el, path)
            val name = stringField(obj, "name", path).trim().takeIf { it.isNotBlank() }
                ?: throw BackupFormatException("$path.name is empty")
            val month = longField(obj, "month", path, default = 0L).toInt()
            val day = longField(obj, "day", path, default = 0L).toInt()
            if (month !in 1..12) throw BackupFormatException("$path.month is invalid")
            if (day !in 1..java.time.Month.of(month).maxLength()) {
                throw BackupFormatException("$path.day is invalid")
            }
            val minutes = longField(obj, "reminderMinutesOfDay", path, default = Birthday.DEFAULT_REMINDER_MINUTES.toLong()).toInt()
            Birthday(
                id = longField(obj, "id", path, default = 0L),
                name = name,
                month = month,
                day = day,
                birthYear = nullableLong(obj, "birthYear")?.toInt(),
                notes = nullableString(obj, "notes"),
                reminderMinutesOfDay = if (minutes in 0..1439) minutes else Birthday.DEFAULT_REMINDER_MINUTES,
                notifyOnDayBefore = obj.get("notifyOnDayBefore")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                contactId = nullableString(obj, "contactId"),
                createdAt = nullableLong(obj, "createdAt") ?: System.currentTimeMillis()
            )
        }
    }

    private fun parseTaskTagCrossRefs(root: JsonObject, version: Long): List<Pair<Long, Long>> {
        val array = root.getAsJsonArray(KEY_TASK_TAG_CROSS_REFS) ?: JsonArray()
        return array.mapIndexed { index, el ->
            val obj = objAsObject(el, "taskTagCrossRefs[$index]")
            val taskId = longField(obj, "taskId", "taskTagCrossRefs[$index]", default = 0L)
            val tagId = longField(obj, "tagId", "taskTagCrossRefs[$index]", default = 0L)
            if (taskId <= 0L || tagId <= 0L) {
                throw BackupFormatException("taskTagCrossRefs[$index] has invalid taskId ($taskId) or tagId ($tagId)")
            }
            Pair(taskId, tagId)
        }
    }

    private fun parseAttachments(root: JsonObject, version: Long): List<Attachment> {
        val array = root.getAsJsonArray(KEY_ATTACHMENTS) ?: JsonArray()
        return array.mapIndexed { index, el ->
            val obj = objAsObject(el, "attachments[$index]")
            val type = try {
                AttachmentType.valueOf(obj.get("type")?.asString ?: "OTHER")
            } catch (e: IllegalArgumentException) {
                throw BackupFormatException("attachments[$index].type contains invalid value")
            }
            Attachment(
                id = longField(obj, "id", "attachments[$index]", default = 0L),
                taskId = longField(obj, "taskId", "attachments[$index]", default = 0L),
                type = type,
                uri = stringField(obj, "uri", "attachments[$index]").takeIf { it.isNotBlank() }
                    ?: throw BackupFormatException("attachments[$index].uri is empty"),
                fileName = stringField(obj, "fileName", "attachments[$index]").takeIf { it.isNotBlank() }
                    ?: "Attachment"
            )
        }
    }

    private fun validateReferences(
        tasks: List<Task>,
        attachments: List<Attachment>,
        categories: List<Category>,
        tags: List<Tag>,
        crossRefs: List<Pair<Long, Long>>
    ) {
        val categoryIds = categories.map { it.id }.filter { it > 0L }.toSet()
        val tagIds = tags.map { it.id }.filter { it > 0L }.toSet()
        val taskIds = tasks.map { it.id }.filter { it > 0L }.toSet()

        // Subtask parent must exist in the backup (or be null). id 0 is allowed:
        // it means the parent task carried no explicit id and will be remapped.
        tasks.forEach { task ->
            val parent = task.parentTaskId
            if (parent != null && parent > 0L && parent !in taskIds) {
                throw BackupFormatException("task ${task.id} references missing parent task $parent")
            }
        }

        // Detect circular parent references in the backup.
        // We need to check for cycles in the parent-child graph within the tasks being imported.
        val parentMap = tasks.associateBy({ it.id }, { it.parentTaskId }) // taskId -> parentTaskId
        tasks.forEach { task ->
            val visited = mutableSetOf<Long>()
            var currentId: Long? = task.id
            while (currentId != null) {
                if (currentId in visited) {
                    throw BackupFormatException("task ${task.id} has circular parent reference")
                }
                visited.add(currentId)
                val parent = parentMap[currentId]
                if (parent == null || parent <= 0L) break
                currentId = parent
            }
        }

        // Category references must point to a category inside this backup.
        tasks.forEach { t ->
            val cat = t.categoryId
            if (cat != null && cat > 0L && cat !in categoryIds) {
                throw BackupFormatException("task ${t.id} references missing category $cat")
            }
        }

        // TaskTagCrossRefs references must point to tasks and tags inside this backup.
        crossRefs.forEach { (taskId, tagId) ->
            if (taskId !in taskIds) {
                throw BackupFormatException("taskTagCrossRef references missing task $taskId")
            }
            if (tagId !in tagIds) {
                throw BackupFormatException("taskTagCrossRef references missing tag $tagId")
            }
        }

        // Attachment task refs must point into this backup. id 0 cannot be
        // remapped and would violate the FK on insert — reject it up front.
        attachments.forEach { a ->
            if (a.taskId <= 0L) {
                throw BackupFormatException("attachment ${a.id} has no valid task reference")
            }
            if (a.taskId !in taskIds) {
                throw BackupFormatException("attachment ${a.id} references missing task ${a.taskId}")
            }
        }
    }

    /**
     * Executes the insert phase. Every entity gets a NEW auto-generated id;
     * references are remapped. Must run inside [transactionRunner.inTransaction]
     * by the caller.
     */
    private suspend fun importParsed(parsed: ParsedBackup) {
        val categoryIdMap = mutableMapOf<Long, Long>()
        val tagIdMap = mutableMapOf<Long, Long>()
        val taskIdMap = mutableMapOf<Long, Long>()

        // 1. Categories → reuse an identical existing one (same name + icon), else new id.
        //    Without this every restore would duplicate the default categories.
        val existingCategories = categoryRepository.getAllCategoriesRaw().toMutableList()
        for (category in parsed.categories) {
            val match = existingCategories.firstOrNull {
                it.name.trim().equals(category.name.trim(), ignoreCase = true) &&
                    it.iconName == category.iconName
            }
            if (match != null) {
                categoryIdMap[category.id] = match.id
            } else {
                val newId = categoryRepository.insertCategory(category.copy(id = 0L)).unwrap()
                categoryIdMap[category.id] = newId
                existingCategories.add(category.copy(id = newId))
            }
        }

        // 2. Tags → check if exists by name, or insert new id.
        for (tag in parsed.tags) {
            val existing = tagRepository.getTagByName(tag.name)
            if (existing is Result.Success && existing.data != null) {
                tagIdMap[tag.id] = existing.data!!.id
            } else {
                val newId = tagRepository.insertTag(tag.copy(id = 0L)).unwrap()
                tagIdMap[tag.id] = newId
            }
        }

        // 3. PASS 1: Insert all tasks with parentTaskId = null, build mapping.
        for (task in parsed.tasks) {
            val newId = taskRepository.insertTask(
                task.copy(
                    id = 0L,
                    categoryId = task.categoryId?.let { categoryIdMap[it] ?: it },
                    parentTaskId = null  // Will be set in PASS 2
                )
            ).unwrap()
            if (task.id > 0L) taskIdMap[task.id] = newId
        }

        // 3. PASS 2: Update child tasks with remapped parentTaskId.
        for (task in parsed.tasks) {
            val newId = taskIdMap[task.id] ?: continue
            val remappedParentId = task.parentTaskId?.let { taskIdMap[it] }
            if (remappedParentId != null) {
                val result = taskRepository.getTaskById(newId)
                if (result is Result.Success && result.data != null) {
                    val updatedTask = result.data!!.copy(parentTaskId = remappedParentId)
                    taskRepository.updateTask(updatedTask)
                }
            }
        }

        // 4. TaskTagCrossRefs → remapped task and tag references.
        for ((oldTaskId, oldTagId) in parsed.taskTagCrossRefs) {
            val newTaskId = taskIdMap[oldTaskId]
            val newTagId = tagIdMap[oldTagId]
            if (newTaskId != null && newTagId != null) {
                tagRepository.insertTaskTagCrossRef(newTaskId, newTagId).unwrap()
            }
        }

        // 5. Attachments → new ids, remapped task reference.
        for (attachment in parsed.attachments) {
            attachmentRepository.insertAttachment(
                attachment.copy(
                    id = 0L,
                    taskId = taskIdMap[attachment.taskId] ?: attachment.taskId
                )
            ).unwrap()
        }

        // 6. Birthdays → new ids (skipping ones already present for the same contact or name+date).
        var importedBirthdays = false
        if (birthdayRepository != null && parsed.birthdays.isNotEmpty()) {
            val existing = birthdayRepository.getAllBirthdaysRaw()
            for (birthday in parsed.birthdays) {
                val duplicate = existing.any {
                    (birthday.contactId != null && it.contactId == birthday.contactId) ||
                        (it.name.trim().equals(birthday.name.trim(), ignoreCase = true) &&
                            it.month == birthday.month && it.day == birthday.day)
                }
                if (!duplicate) {
                    birthdayRepository.insertBirthday(birthday.copy(id = 0L)).unwrap()
                    importedBirthdays = true
                }
            }
        }

        // 7. Restore AUTOINCREMENT so future inserts never collide with imported ids.
        val tables = mutableListOf("categories", "tags", "tasks", "attachments")
        if (importedBirthdays) tables.add("birthdays")
        backupMaintenance.resetSequences(tables)
    }

    private fun <T> Result<T>.unwrap(): T = when (this) {
        is Result.Success -> data
        is Result.Error -> throw exception
        is Result.Loading -> throw IllegalStateException("Loading is not a terminal result")
    }

    private fun objAsObject(el: JsonElement, path: String): JsonObject {
        if (!el.isJsonObject) throw BackupFormatException("$path is not an object")
        return el.asJsonObject
    }

    private fun stringField(obj: JsonObject, name: String, path: String): String {
        val el = obj.get(name) ?: throw BackupFormatException("$path.$name is missing")
        if (el.isJsonNull) throw BackupFormatException("$path.$name is null")
        return el.asString
    }

    private fun longField(obj: JsonObject, name: String, path: String, default: Long): Long {
        val el = obj.get(name) ?: return default
        if (el.isJsonNull) return default
        return el.asLong
    }

    private fun nullableLong(obj: JsonObject, name: String): Long? {
        val el = obj.get(name) ?: return null
        if (el.isJsonNull) return null
        return el.asLong
    }

    private fun nullableString(obj: JsonObject, name: String): String? {
        val el = obj.get(name) ?: return null
        if (el.isJsonNull) return null
        return el.asString
    }

    private fun nullableInstant(obj: JsonObject, name: String, path: String): Instant? {
        val el = obj.get(name) ?: return null
        if (el.isJsonNull) return null
        return try {
            Instant.parse(el.asString)
        } catch (e: Exception) {
            throw BackupFormatException("$path.$name is not a valid ISO-8601 instant")
        }
    }
}