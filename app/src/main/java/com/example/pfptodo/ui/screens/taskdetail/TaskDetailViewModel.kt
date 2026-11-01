package com.example.pfptodo.ui.screens.taskdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Attachment
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.repository.AttachmentRepository
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ViewModel for the Task Detail screen.
 * Handles loading and display of a specific task's details, including its subtasks and attachments.
 *
 * Operation failures (subtask toggle/delete, attachment add/remove, reorder, completion)
 * surface through [TaskDetailUiState.error] instead of being silently discarded; the
 * screen shows the message once and calls [onErrorShown].
 *
 * @param taskId The unique ID of the task to display.
 */
class TaskDetailViewModel(
    private val taskId: Long,
    private val taskUseCases: TaskUseCases,
    private val attachmentRepository: AttachmentRepository,
    private val reminderManager: ReminderManager
) : ViewModel() {

    private val _operationError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TaskDetailUiState> = combine(
        taskUseCases.observeTaskById(taskId),
        _operationError
    ) { task, error ->
        TaskDetailUiState(task = task, isLoading = false, error = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskDetailUiState(isLoading = true)
    )

    val subtasks: Flow<List<Task>> = taskUseCases.getSubtasks(taskId)
    val attachments: Flow<List<Attachment>> = attachmentRepository.getAttachmentsForTask(taskId)

    /** Called by the screen after the error has been displayed. */
    fun onErrorShown() {
        _operationError.value = null
    }

    private fun reportIfError(result: Result<*>?) {
        if (result is Result.Error) {
            _operationError.value = result.exception.localizedMessage
                ?: result.exception.message
                ?: "Operation failed"
        }
    }

    fun onToggleSubtask(subtask: Task) {
        viewModelScope.launch {
            val now = Instant.now()
            val isNowCompleted = !subtask.isCompleted
            reportIfError(
                taskUseCases.updateTask(
                    subtask.copy(
                        isCompleted = isNowCompleted,
                        completedAt = if (isNowCompleted) now else null,
                        updatedAt = now
                    )
                )
            )
        }
    }

    fun onDeleteTask() {
        viewModelScope.launch {
            uiState.value.task?.let { task ->
                reportIfError(taskUseCases.deleteTask(task))
                reminderManager.cancelReminder(task.id)
            }
        }
    }

    fun onAddSubtask(title: String) {
        val lines = title.split("\n", "\r\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        viewModelScope.launch {
            if (lines.size > 1) {
                reportIfError(taskUseCases.createSubtasksBatch(taskId, lines))
            } else {
                reportIfError(taskUseCases.createSubtask(taskId, lines[0]))
            }
        }
    }

    fun onReorderSubtasks(orderedIds: List<Long>) {
        viewModelScope.launch {
            reportIfError(taskUseCases.reorderSubtasks(taskId, orderedIds))
        }
    }

    fun onDeleteSubtask(subtask: Task) {
        viewModelScope.launch {
            reportIfError(taskUseCases.deleteTask(subtask))
        }
    }

    fun onAddAttachment(attachment: Attachment) {
        viewModelScope.launch {
            reportIfError(attachmentRepository.insertAttachment(attachment))
        }
    }

    fun onDeleteAttachment(attachment: Attachment) {
        viewModelScope.launch {
            reportIfError(attachmentRepository.deleteAttachment(attachment))
        }
    }

    fun onToggleTaskCompletion() {
        viewModelScope.launch {
            uiState.value.task?.let { task ->
                reportIfError(taskUseCases.toggleTaskCompletion(task))
            }
        }
    }
}

data class TaskDetailUiState(
    val task: Task? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
