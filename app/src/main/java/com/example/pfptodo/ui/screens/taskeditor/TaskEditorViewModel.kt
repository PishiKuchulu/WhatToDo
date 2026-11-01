package com.example.pfptodo.ui.screens.taskeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.R
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.usecase.category.CategoryUseCases
import com.example.pfptodo.domain.usecase.tag.TagUseCases
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ViewModel for the Task Editor screen.
 * Handles the creation and editing of tasks, including validation, category & tag selection, and persistence logic.
 *
 * @param taskId Optional ID of the task being edited (null for new tasks).
 * @param parentTaskId Optional ID of the parent task if creating a subtask.
 */
class TaskEditorViewModel(
    private val taskId: Long?,
    private val parentTaskId: Long?,
    private val taskUseCases: TaskUseCases,
    private val tagUseCases: TagUseCases,
    private val reminderManager: ReminderManager,
    private val categoryUseCases: CategoryUseCases? = null,
    private val prefill: TaskPrefill? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskEditorUiState(isEditing = taskId != null))
    val uiState = _uiState.asStateFlow()

    // Store original reminderTime to detect changes on edit
    private var originalReminderTime: Instant? = null

    /**
     * The persisted task being edited. Saving builds on top of it so fields the editor does
     * not expose (notes, completion state, parent, manual order, creation date, series
     * anchor) are preserved instead of being reset to defaults.
     */
    private var originalTask: Task? = null

    /**
     * The task's persisted subtasks as loaded, kept so saving can reconcile the editor's
     * rows against them (delete removed, rename changed, insert new) instead of blindly
     * appending the whole list again. [subtasksLoaded] stays false when the load failed —
     * reconciliation then only appends new rows and never deletes, so a failed load can
     * never wipe the checklist.
     */
    private var originalSubtasks: List<Task> = emptyList()
    private var subtasksLoaded: Boolean = false

    init {
        viewModelScope.launch {
            tagUseCases.getAllTags().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }

        categoryUseCases?.let { cu ->
            viewModelScope.launch {
                cu.getAllCategories().collect { categories ->
                    _uiState.update { it.copy(availableCategories = categories) }
                }
            }
        }

        if (taskId != null) {
            loadTask(taskId)
        } else if (prefill != null && !prefill.isEmpty) {
            applyPrefill(prefill)
        }
    }

    private fun applyPrefill(p: TaskPrefill) {
        _uiState.update {
            it.copy(
                title = p.title,
                priority = p.priority,
                dueDate = p.dueDate,
                recurrenceRule = p.recurrenceRule,
                reminderTime = if (p.hasReminder) p.dueDate else null,
                subtasks = p.subtaskTitles.map { EditorSubtaskRow(title = it) }
            )
        }
        if (p.tagNames.isNotEmpty()) {
            viewModelScope.launch {
                val ids = mutableSetOf<Long>()
                for (name in p.tagNames) {
                    try {
                        val existing = (tagUseCases.getTagByName(name) as? Result.Success)?.data
                        if (existing != null) {
                            ids.add(existing.id)
                        } else {
                            val created = tagUseCases.addTag(Tag(name = name))
                            if (created is Result.Success) ids.add(created.data)
                        }
                    } catch (_: Exception) {
                        // A tag that cannot be resolved is simply not preselected.
                    }
                }
                if (ids.isNotEmpty()) {
                    _uiState.update { state -> state.copy(selectedTagIds = state.selectedTagIds + ids) }
                }
            }
        }
    }

    private fun loadTask(id: Long) {
        viewModelScope.launch {
            val result = taskUseCases.getTaskById(id)
            if (result is Result.Success) {
                val task = result.data
                if (task != null) {
                    originalTask = task
                    originalReminderTime = task.reminderTime
                    val selectedIds = task.tags.map { it.id }.toSet()
                    _uiState.update { it.copy(
                        title = task.title,
                        description = task.description ?: "",
                        notes = task.notes ?: "",
                        priority = task.priority,
                        selectedCategoryId = task.categoryId,
                        dueDate = task.dueDate,
                        recurrenceRule = task.recurrenceRule,
                        reminderTime = task.reminderTime,
                        selectedTagIds = selectedIds
                    ) }
                    loadSubtasks(id)
                }
            }
        }
    }

    /**
     * Loads the task's existing subtasks into the editor as rows carrying their persisted
     * ids, so the checklist can be edited (renamed/removed) rather than duplicated on save.
     */
    private suspend fun loadSubtasks(taskId: Long) {
        try {
            val existing = taskUseCases.getSubtasks(taskId).first()
            originalSubtasks = existing
            subtasksLoaded = true
            _uiState.update {
                it.copy(subtasks = existing.map { sub -> EditorSubtaskRow(id = sub.id, title = sub.title) })
            }
        } catch (e: Exception) {
            // The editor still works for the task itself; save-time reconciliation stays
            // append-only (see reconcileSubtasks) so nothing can be lost.
            subtasksLoaded = false
        }
    }

    fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onDescriptionChange(newDesc: String) {
        _uiState.update { it.copy(description = newDesc) }
    }

    fun onPriorityChange(newPriority: Priority) {
        _uiState.update { it.copy(priority = newPriority) }
    }

    fun onSelectCategory(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onNotesChange(newNotes: String) {
        _uiState.update { it.copy(notes = newNotes) }
    }

    fun onDueDateChange(newDate: Instant?) {
        _uiState.update { state ->
            // A reminder that was following the due date keeps following it.
            val followsDueDate = state.reminderTime != null && state.reminderTime == state.dueDate
            state.copy(
                dueDate = newDate,
                reminderTime = if (followsDueDate) newDate else state.reminderTime
            )
        }
    }

    fun onRecurrenceChange(newRule: RecurrenceRule) {
        _uiState.update { it.copy(recurrenceRule = newRule) }
    }

    fun onReminderChange(newTime: Instant?) {
        _uiState.update { it.copy(reminderTime = newTime) }
    }

    fun onToggleTag(tagId: Long) {
        _uiState.update { state ->
            val updated = if (tagId in state.selectedTagIds) {
                state.selectedTagIds - tagId
            } else {
                state.selectedTagIds + tagId
            }
            state.copy(selectedTagIds = updated)
        }
    }

    fun onCreateTag(name: String, color: String?) {
        viewModelScope.launch {
            val newTag = Tag(name = name, color = color)
            val result = tagUseCases.addTag(newTag)
            if (result is Result.Success) {
                val createdTagId = result.data
                _uiState.update { state ->
                    state.copy(selectedTagIds = state.selectedTagIds + createdTagId)
                }
            }
        }
    }

    fun onAddSubtask(title: String) {
        val trimmed = title.trim()
        if (trimmed.isNotBlank()) {
            _uiState.update { it.copy(subtasks = it.subtasks + EditorSubtaskRow(title = trimmed)) }
        }
    }

    fun onRemoveSubtask(index: Int) {
        _uiState.update { state ->
            if (index in state.subtasks.indices) {
                val updated = state.subtasks.toMutableList().apply { removeAt(index) }
                state.copy(subtasks = updated)
            } else {
                state
            }
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.title.isBlank() || state.isSaving) return
        // Never save an edit before the original task has loaded: that would overwrite it with defaults.
        if (taskId != null && originalTask == null) return

        _uiState.update { it.copy(isSaving = true, saveError = null, saveErrorRes = null) }

        viewModelScope.launch {
            try {
                val base = originalTask
                val task = (base ?: Task(title = state.title.trim())).copy(
                    id = taskId ?: 0,
                    title = state.title.trim(),
                    description = state.description.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    priority = state.priority,
                    categoryId = state.selectedCategoryId,
                    dueDate = state.dueDate,
                    recurrenceRule = state.recurrenceRule,
                    reminderTime = state.reminderTime,
                    // Editing keeps the original parent; only brand-new tasks take the nav argument.
                    parentTaskId = if (base != null) base.parentTaskId else parentTaskId,
                    recurrenceAnchor = resolveRecurrenceAnchor(base, state),
                    updatedAt = Instant.now()
                )

                val targetTaskId: Long? = if (taskId == null) {
                    val result = taskUseCases.addTask(task)
                    if (result is Result.Success) {
                        val newId = result.data
                        val newTitles = state.subtasks.map { it.title }
                        val batchFailed = newTitles.isNotEmpty() &&
                            taskUseCases.createSubtasksBatch(newId, newTitles) is Result.Error
                        if (batchFailed) {
                            // Subtask write failed: the save as a whole failed — do not
                            // schedule the reminder or report success.
                            null
                        } else {
                            if (task.reminderTime != null) {
                                reminderManager.scheduleReminder(task.copy(id = newId))
                            }
                            newId
                        }
                    } else {
                        reminderManager.cancelReminder(task.id)
                        null
                    }
                } else {
                    val updateResult = taskUseCases.updateTask(task)
                    if (updateResult is Result.Error) {
                        null
                    } else {
                        // Sync the checklist against what was loaded: delete removed rows,
                        // rename changed rows, insert new rows. Any failure fails the save
                        // as a whole, exactly like the batch failure did before.
                        val subtaskSyncError = reconcileSubtasks(task.id, state.subtasks)
                        if (subtaskSyncError != null) {
                            null
                        } else {
                            // Handle reminder changes on edit
                            val newReminderTime = state.reminderTime
                            if (originalReminderTime != null && newReminderTime == null) {
                                reminderManager.cancelReminder(task.id)
                            } else if (originalReminderTime != null && newReminderTime != null) {
                                reminderManager.cancelReminder(task.id)
                                reminderManager.scheduleReminder(task)
                            } else if (originalReminderTime == null && newReminderTime != null) {
                                reminderManager.scheduleReminder(task)
                            }
                            task.id
                        }
                    }
                }

                if (targetTaskId != null && targetTaskId > 0L) {
                    val tagsResult = tagUseCases.setTaskTags(targetTaskId, state.selectedTagIds.toList())
                    if (tagsResult is Result.Error) {
                        _uiState.update { it.copy(isSaving = false, saveErrorRes = R.string.save_task_failed, saveError = tagsResult.exception.localizedMessage) }
                    } else {
                        _uiState.update { it.copy(isSaving = false, isSaved = true) }
                    }
                } else {
                    _uiState.update { it.copy(isSaving = false, saveErrorRes = R.string.save_task_failed, saveError = null) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveErrorRes = R.string.error_saving_task, saveError = e.localizedMessage) }
            }
        }
    }

    /**
     * Reconciles the editor's subtask rows with the persisted subtasks of [taskId]:
     * rows whose persisted id is gone are deleted, kept rows with a changed title are
     * renamed in place (preserving completion state and order), and rows without an id
     * are batch-inserted. Returns the failure exception, or null when everything synced.
     *
     * When the original subtasks never loaded ([subtasksLoaded] is false) only the
     * inserts run — deleting based on an unknown original state could wipe the checklist.
     */
    private suspend fun reconcileSubtasks(taskId: Long, rows: List<EditorSubtaskRow>): Throwable? {
        if (subtasksLoaded) {
            val keptIds = rows.mapNotNull { it.id }.toSet()
            for (original in originalSubtasks) {
                if (original.id !in keptIds) {
                    val deleteResult = taskUseCases.deleteTask(original)
                    if (deleteResult is Result.Error) return deleteResult.exception
                }
            }
            for (row in rows) {
                val rowId = row.id ?: continue
                val original = originalSubtasks.firstOrNull { it.id == rowId } ?: continue
                if (original.title != row.title) {
                    val renameResult = taskUseCases.updateTask(
                        original.copy(title = row.title, updatedAt = Instant.now())
                    )
                    if (renameResult is Result.Error) return renameResult.exception
                }
            }
        }
        val newTitles = rows.filter { it.id == null }.map { it.title }.filter { it.isNotBlank() }
        if (newTitles.isNotEmpty()) {
            val batchResult = taskUseCases.createSubtasksBatch(taskId, newTitles)
            if (batchResult is Result.Error) return batchResult.exception
        }
        return null
    }

    /**
     * Series anchor: kept while the series is unchanged, re-based on the due date when the
     * rule is newly set or the due date moves, and cleared when the task stops repeating.
     */
    private fun resolveRecurrenceAnchor(base: Task?, state: TaskEditorUiState): Instant? {
        if (state.recurrenceRule == RecurrenceRule.None) return null
        if (base == null) return state.dueDate ?: Instant.now()
        val unchanged = base.recurrenceRule == state.recurrenceRule && base.dueDate == state.dueDate
        return if (unchanged && base.recurrenceAnchor != null) {
            base.recurrenceAnchor
        } else {
            state.dueDate ?: base.recurrenceAnchor ?: Instant.now()
        }
    }
}

/**
 * One checklist row in the editor. [id] is the persisted subtask id, or null for a row
 * typed in this session that has not been saved yet.
 */
data class EditorSubtaskRow(
    val id: Long? = null,
    val title: String = ""
)

data class TaskEditorUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val selectedCategoryId: Long? = null,
    val dueDate: Instant? = null,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.None,
    val reminderTime: Instant? = null,
    val availableCategories: List<Category> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val subtasks: List<EditorSubtaskRow> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveError: String? = null,
    /** Localized save-failure message shown in the snackbar; [saveError] carries raw detail if any. */
    val saveErrorRes: Int? = null
)