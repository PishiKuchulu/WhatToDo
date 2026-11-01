package com.example.pfptodo.ui.screens.birthday

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.core.util.ContactBirthdayReader
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Birthday
import com.example.pfptodo.domain.usecase.birthday.BirthdayUseCases
import com.example.pfptodo.domain.usecase.birthday.UpcomingBirthday
import com.example.pfptodo.worker.reminder.BirthdayReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * Minimal list-level error surface (e.g. a failed delete). The birthday list
     * screen does not render this yet — it exists so failures are not silent.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun openEditorForNew(imported: ImportedContact? = null) {
        _editorState.value = BirthdayEditorState(
            name = imported?.name.orEmpty(),
            // No fabricated 1/1: when the source contact has no birthday, month/day
            // stay unknown (null) until the user picks a date, and save() refuses
            // to run while they are missing (M-18).
            month = imported?.month,
            day = imported?.day,
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

    /**
     * Handles a contact chosen in the system picker. The ContentResolver queries
     * run on [Dispatchers.IO]; the screen's picker callback only forwards the URI
     * (and a resolver) here (M-19).
     */
    fun onContactPicked(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                ContactBirthdayReader.read(resolver, uri)
            }
            if (data != null) {
                openEditorForNew(
                    ImportedContact(
                        name = data.name,
                        month = data.month,
                        day = data.day,
                        year = data.year,
                        contactId = data.contactId
                    )
                )
            } else {
                openEditorForNew()
            }
        }
    }

    fun closeEditor() {
        _isEditorOpen.value = false
    }

    fun clearError() {
        _error.value = null
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
        val month = state.month
        val day = state.day
        if (month == null || day == null) {
            // Incomplete record (e.g. a contact imported without a birthday):
            // never invent a date — block the save and point at the missing field.
            _editorState.value = state.copy(error = if (month == null) "bad_month" else "bad_day")
            return
        }
        val error = useCases.validateBirthday(
            name = state.name,
            month = month,
            day = day,
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
                month = month,
                day = day,
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
            // Cancel the reminder only when the delete actually succeeded —
            // cancelling on failure would silence reminders for a record that
            // is still stored (M-21).
            when (val result = useCases.deleteBirthday(birthday)) {
                is Result.Success -> reminderManager.cancelReminder(birthday.id)
                is Result.Error -> _error.value = "delete_failed"
                is Result.Loading -> Unit
            }
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
    /** Gregorian month (1-12), or null while unknown (contact imported without a birthday). */
    val month: Int? = null,
    /** Gregorian day of month (1-31), or null while unknown. */
    val day: Int? = null,
    val birthYear: Int? = null,
    val notes: String = "",
    val reminderMinutesOfDay: Int = Birthday.DEFAULT_REMINDER_MINUTES,
    val notifyOnDayBefore: Boolean = false,
    val contactId: String? = null,
    val error: String? = null
) {
    /**
     * Whether a birth year is known. Date pickers must write back a year only
     * when this is true — otherwise a year-less record gains the picker's
     * reference year merely by opening and confirming the picker (M-20).
     */
    val yearKnown: Boolean get() = birthYear != null
}
