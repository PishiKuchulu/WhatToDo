package com.example.pfptodo.ui.screens.taskdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * @param taskId The unique ID of the task to display.
 */
class TaskDetailViewModel(
    private val taskId: Long,
    private val taskUseCases: TaskUseCases,
    private val attachmentRepository: AttachmentRepository,
    private val reminderManager: ReminderManager
) : ViewModel() {

    val uiState: StateFlow<TaskDetailUiState> = taskUseCases.observeTaskById(taskId)
        .map { task ->
            TaskDetailUiState(task = task, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TaskDetailUiState(isLoading = true)
        )

    val subtasks: Flow<List<Task>> = taskUseCases.getSubtasks(taskId)
    val attachments: Flow<List<Attachment>> = attachmentRepository.getAttachmentsForTask(taskId)

    fun onToggleSubtask(subtask: Task) {
        viewModelScope.launch {
            val now = Instant.now()
            val isNowCompleted = !subtask.isCompleted
            taskUseCases.updateTask(
                subtask.copy(
                    isCompleted = isNowCompleted,
                    completedAt = if (isNowCompleted) now else null,
                    updatedAt = now
                )
            )
        }
    }

    fun onDeleteTask() {
        viewModelScope.launch {
            uiState.value.task?.let { task ->
                taskUseCases.deleteTask(task)
                reminderManager.cancelReminder(task.id)
            }
        }
    }

    fun onAddSubtask(title: String) {
        val lines = title.split("\n", "\r\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        viewModelScope.launch {
            if (lines.size > 1) {
                taskUseCases.createSubtasksBatch(taskId, lines)
            } else {
                taskUseCases.createSubtask(taskId, lines[0])
            }
        }
    }

    fun onReorderSubtasks(orderedIds: List<Long>) {
        viewModelScope.launch {
            taskUseCases.reorderSubtasks(taskId, orderedIds)
        }
    }

    fun onDeleteSubtask(subtask: Task) {
        viewModelScope.launch {
            taskUseCases.deleteTask(subtask)
        }
    }

    fun onAddAttachment(attachment: Attachment) {
        viewModelScope.launch {
            attachmentRepository.insertAttachment(attachment)
        }
    }

    fun onDeleteAttachment(attachment: Attachment) {
        viewModelScope.launch {
            attachmentRepository.deleteAttachment(attachment)
        }
    }

    fun onToggleTaskCompletion() {
        viewModelScope.launch {
            uiState.value.task?.let { task ->
                taskUseCases.toggleTaskCompletion(task)
            }
        }
    }
}

data class TaskDetailUiState(
    val task: Task? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
