package com.example.pfptodo.ui.screens.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.domain.model.EisenhowerMatrix
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Eisenhower Matrix Screen.
 * Subscribes to partitioned matrix state and handles quick task actions within quadrants.
 */
class EisenhowerMatrixViewModel(
    private val taskUseCases: TaskUseCases
) : ViewModel() {

    val matrixState: StateFlow<EisenhowerMatrix> = taskUseCases.getEisenhowerMatrix()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EisenhowerMatrix()
        )

    fun onToggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskUseCases.toggleTaskCompletion(task)
        }
    }

    fun onSnoozeTask(task: Task) {
        viewModelScope.launch {
            taskUseCases.rescheduleTask.snoozeDays(task, 1)
        }
    }

    fun onDeleteTask(task: Task) {
        viewModelScope.launch {
            taskUseCases.deleteTask(task)
        }
    }
}
