package com.example.pfptodo.ui.screens.birthday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.usecase.birthday.BirthdayUseCases
import com.example.pfptodo.domain.usecase.birthday.UpcomingBirthday
import com.example.pfptodo.worker.reminder.BirthdayReminderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class BirthdayViewModel(
    private val useCases: BirthdayUseCases,
    private val reminderManager: BirthdayReminderManager
) : ViewModel() {

    val upcoming: StateFlow<List<UpcomingBirthday>> =
        useCases.getUpcomingBirthdays().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _editorState = MutableStateFlow(BirthdayEditorState())
    val editorState = _editorState.asStateFlow()

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen = _isEditorOpen.asStateFlow()

    fun openEditorForNew(imported: ImportedContact? = null) {
        _editorState.value = BirthdayEditorState(
            name = imported?.name.orEmpty(),
            month = imported?.month ?: 1,
            day = imported?.day ?: 1,
            birthYear = imported?.year,
            contactId = imported?.contactId
        )
        _isEditorOpen.value = true
    }

    fun openEditorForEdit(birthday: Birthday) {
        _editorState.value = BirthdayEditorState(
            id = birthday.id,
            name = birthday.name,
            month = birthday.month,
            day = birthday.day,
            birthYear = birthday.birthYear,
            notes = birthday.notes.orEmpty(),
            reminderMinutesOfDay = birthday.reminderMinutesOfDay,
            notifyOnDayBefore = birthday.notifyOnDayBefore,
            contactId = birthday.contactId
        )
        _isEditorOpen.value = true
    }

    fun closeEditor() {
        _isEditorOpen.value = false
    }

    fun onNameChange(value: String) = _editorState.update { it.copy(name = value) }
    fun onMonthChange(value: Int) = _editorState.update { it.copy(month = value) }
    fun onDayChange(value: Int) = _editorState.update { it.copy(day = value) }
    fun onYearChange(value: String) = _editorState.update {
        it.copy(birthYear = value.toIntOrNull())
    }
    fun onNotesChange(value: String) = _editorState.update { it.copy(notes = value) }
    fun onReminderTimeChange(minutesOfDay: Int) = _editorState.update {
        it.copy(reminderMinutesOfDay = minutesOfDay)
    }
    fun onNotifyDayBeforeChange(value: Boolean) = _editorState.update {
        it.copy(notifyOnDayBefore = value)
    }

    fun save() {
        val state = _editorState.value
        val error = useCases.validateBirthday(
            name = state.name,
            month = state.month,
            day = state.day,
            birthYear = state.birthYear,
            today = LocalDate.now()
        )
        if (error != null) {
            _editorState.value = state.copy(error = error)
            return
        }

        viewModelScope.launch {
            val birthday = Birthday(
                id = state.id,
                name = state.name.trim(),
                month = state.month,
                day = state.day,
                birthYear = state.birthYear,
                notes = state.notes.ifBlank { null },
                reminderMinutesOfDay = state.reminderMinutesOfDay,
                notifyOnDayBefore = state.notifyOnDayBefore,
                contactId = state.contactId
            )
            val result: Result<Long> = if (state.id == 0L) {
                useCases.addBirthday(birthday)
            } else {
                when (val r = useCases.updateBirthday(birthday)) {
                    is Result.Success -> Result.Success(state.id)
                    is Result.Error -> Result.Error(r.exception)
                    is Result.Loading -> Result.Error(IllegalStateException("update in progress"))
                }
            }

            if (result is Result.Success) {
                reminderManager.scheduleReminder(birthday.copy(id = result.data))
                _isEditorOpen.value = false
            } else {
                _editorState.value = state.copy(error = "save_failed")
            }
        }
    }

    fun delete(birthday: Birthday) {
        viewModelScope.launch {
            useCases.deleteBirthday(birthday)
            reminderManager.cancelReminder(birthday.id)
        }
    }
}

/** Contact data handed from the system picker into the editor. */
data class ImportedContact(
    val name: String?,
    val month: Int?,
    val day: Int?,
    val year: Int?,
    val contactId: String?
)

data class BirthdayEditorState(
    val id: Long = 0,
    val name: String = "",
    val month: Int = 1,
    val day: Int = 1,
    val birthYear: Int? = null,
    val notes: String = "",
    val reminderMinutesOfDay: Int = Birthday.DEFAULT_REMINDER_MINUTES,
    val notifyOnDayBefore: Boolean = false,
    val contactId: String? = null,
    val error: String? = null
)
