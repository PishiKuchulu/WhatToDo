package com.example.pfptodo.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.core.repository.LanguageRepository
import com.example.pfptodo.core.repository.ThemeRepository
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.usecase.category.CategoryUseCases
import com.example.pfptodo.domain.usecase.backup.BackupUseCases
import com.example.pfptodo.domain.usecase.tag.TagUseCases
import com.example.pfptodo.ui.theme.AppTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Result of an export/import operation, mapped to localized messages by the UI. */
enum class TransferStatus {
    SUCCESS,
    ERROR,
    CANCELLED,
    INVALID
}

/**
 * ViewModel for the Settings screen: theme, language, tags, and data backup.
 * Export/import run one at a time; a second invocation while busy is ignored.
 */
class SettingsViewModel(
    private val themeRepository: ThemeRepository,
    private val languageRepository: LanguageRepository,
    private val tagUseCases: TagUseCases,
    private val backupUseCases: BackupUseCases,
    private val categoryUseCases: CategoryUseCases? = null,
    private val calendarRepository: com.example.pfptodo.core.repository.CalendarRepository? = null,
    /** Runs after a successful import, e.g. to re-arm reminders for the restored data. */
    private val onImported: suspend () -> Unit = {}
) : ViewModel() {

    private val _exportStatus = MutableStateFlow<TransferStatus?>(null)
    val exportStatus = _exportStatus.asStateFlow()

    private val _importStatus = MutableStateFlow<TransferStatus?>(null)
    val importStatus = _importStatus.asStateFlow()

    /** True while an export/import is in flight. */
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    val themeFlow: StateFlow<AppTheme> = themeRepository.themeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppTheme.SYSTEM
    )

    val languageFlow: StateFlow<String> = languageRepository.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "system"
    )

    val tagsFlow: StateFlow<List<Tag>> = tagUseCases.getAllTags().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categoriesFlow: StateFlow<List<Category>> =
        (categoryUseCases?.getAllCategories() ?: flowOf(emptyList())).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Display calendar (Gregorian / Jalali). Stored task and birthday dates are unaffected. */
    val calendarTypeFlow: StateFlow<com.example.pfptodo.core.util.CalendarType> =
        (calendarRepository?.calendarTypeFlow ?: flowOf(com.example.pfptodo.core.util.CalendarType.defaultFor(java.util.Locale.getDefault())))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = com.example.pfptodo.core.util.CalendarType.defaultFor(java.util.Locale.getDefault())
            )

    fun onCalendarTypeSelected(type: com.example.pfptodo.core.util.CalendarType) {
        viewModelScope.launch { calendarRepository?.setCalendarType(type) }
    }

    fun onCreateCategory(name: String, color: String, iconName: String) {
        viewModelScope.launch {
            categoryUseCases?.addCategory?.invoke(Category(name = name, color = color, iconName = iconName))
        }
    }

    fun onUpdateCategory(category: Category) {
        viewModelScope.launch { categoryUseCases?.updateCategory?.invoke(category) }
    }

    /** Tasks in the deleted category become uncategorized (FK SET_NULL), they are never deleted. */
    fun onDeleteCategory(category: Category) {
        viewModelScope.launch { categoryUseCases?.deleteCategory?.invoke(category) }
    }

    fun onThemeSelected(theme: AppTheme) {
        viewModelScope.launch {
            themeRepository.setTheme(theme)
        }
    }

    fun onLanguageSelected(languageTag: String) {
        viewModelScope.launch {
            languageRepository.setLanguage(languageTag)
        }
    }

    fun onCreateTag(name: String, color: String?) {
        viewModelScope.launch {
            tagUseCases.addTag(Tag(name = name, color = color))
        }
    }

    fun onUpdateTag(tag: Tag) {
        viewModelScope.launch {
            tagUseCases.updateTag(tag)
        }
    }

    fun onDeleteTag(tag: Tag) {
        viewModelScope.launch {
            tagUseCases.deleteTag(tag)
        }
    }

    fun exportData(context: Context, uri: Uri) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val json = backupUseCases.exportToJson()
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    _exportStatus.value = TransferStatus.ERROR
                    return@launch
                }
                stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                _exportStatus.value = TransferStatus.SUCCESS
            } catch (e: Exception) {
                _exportStatus.value = TransferStatus.ERROR
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    _importStatus.value = TransferStatus.ERROR
                    return@launch
                }
                val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val result = backupUseCases.importFromJson(json)
                if (result is Result.Success) {
                    try { onImported() } catch (e: Exception) { /* reminders are best effort */ }
                }
                _importStatus.value = if (result is Result.Success) {
                    TransferStatus.SUCCESS
                } else {
                    TransferStatus.INVALID
                }
            } catch (e: Exception) {
                _importStatus.value = TransferStatus.ERROR
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun clearStatus() {
        _exportStatus.value = null
        _importStatus.value = null
    }
}