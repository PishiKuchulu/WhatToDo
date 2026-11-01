package com.example.pfptodo.ui.screens.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.R
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.EisenhowerMatrix
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * String resource of the last failed task operation (toggle/snooze/delete), or null.
     * The screen observes this, shows it once, and calls [onOperationErrorShown].
     */
    private val _operationError = MutableStateFlow<Int?>(null)
    val operationError = _operationError.asStateFlow()

    fun onOperationErrorShown() {
        _operationError.value = null
    }

    private fun reportIfError(result: Result<*>?) {
        if (result is Result.Error) {
            _operationError.value = R.string.operation_failed
        }
    }

    fun onToggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            reportIfError(taskUseCases.toggleTaskCompletion(task))
        }
    }

    fun onSnoozeTask(task: Task) {
        viewModelScope.launch {
            reportIfError(taskUseCases.rescheduleTask.snoozeDays(task, 1))
        }
    }

    fun onDeleteTask(task: Task) {
        viewModelScope.launch {
            reportIfError(taskUseCases.deleteTask(task))
        }
    }
}
