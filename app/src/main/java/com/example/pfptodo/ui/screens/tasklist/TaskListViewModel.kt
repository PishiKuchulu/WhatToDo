package com.example.pfptodo.ui.screens.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.core.repository.ThemeRepository
import com.example.pfptodo.core.util.Result
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.ParsedTask
import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.domain.usecase.category.CategoryUseCases
import com.example.pfptodo.domain.usecase.tag.TagUseCases
import com.example.pfptodo.domain.usecase.task.ParseSmartTaskUseCase
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import com.example.pfptodo.ui.theme.AppTheme
import com.example.pfptodo.worker.reminder.ReminderManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel for the main Task List screen.
 * Orchestrates task filtering, searching, sorting, smart task parsing, and core task actions.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TaskListViewModel(
    private val taskUseCases: TaskUseCases,
    private val tagUseCases: TagUseCases,
    private val parseSmartTaskUseCase: ParseSmartTaskUseCase,
    private val themeRepository: ThemeRepository,
    private val reminderManager: ReminderManager,
    private val categoryUseCases: CategoryUseCases? = null,
    calendarRepository: com.example.pfptodo.core.repository.CalendarRepository? = null
) : ViewModel() {

    private val calendarTypeFlow: Flow<com.example.pfptodo.core.util.CalendarType?> =
        calendarRepository?.calendarTypeFlow ?: flowOf(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(TaskFilter())

    /**
     * Serializes due-date writes: snooze and undo-restore launch independent coroutines,
     * and a rapid undo could otherwise race the snooze write.
     */
    private val dueDateMutex = Mutex()
    val filter = _filter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.MANUAL)
    val sortOrder = _sortOrder.asStateFlow()

    private val _isSettingsSheetOpen = MutableStateFlow(false)
    val isSettingsSheetOpen = _isSettingsSheetOpen.asStateFlow()

    private val availableTags: Flow<List<Tag>> = tagUseCases.getAllTags()
    private val availableCategories: Flow<List<Category>> = categoryUseCases?.getAllCategories() ?: flowOf(emptyList())

    private val debouncedSearchQuery: Flow<String> = _searchQuery
        .debounce { if (it.isBlank()) 0L else 300L }
        .distinctUntilChanged()

    private val parsedSearchQueryFlow: Flow<com.example.pfptodo.domain.model.ParsedSearchQuery> = debouncedSearchQuery
        .map { query -> taskUseCases.parseNaturalLanguageSearch(query) }
        .distinctUntilChanged()

    private val filteredTasksFlow: Flow<List<Task>> = combine(
        parsedSearchQueryFlow,
        _filter,
        _sortOrder,
        calendarTypeFlow
    ) { parsedSearch, filter, sort, calendarType ->
        val mergedFilter = filter.copy(
            calendarType = calendarType,
            status = if (parsedSearch.filter.status != TaskFilter.Status.ALL) parsedSearch.filter.status else filter.status,
            priorities = if (parsedSearch.filter.priorities.isNotEmpty()) filter.priorities + parsedSearch.filter.priorities else filter.priorities,
            dateFilter = if (parsedSearch.filter.dateFilter != TaskFilter.DateFilter.ALL) parsedSearch.filter.dateFilter else filter.dateFilter,
            isRecurringOnly = if (parsedSearch.filter.isRecurringOnly) true else filter.isRecurringOnly
        )
        Triple(parsedSearch.keyword, mergedFilter, sort)
    }.distinctUntilChanged()
    .flatMapLatest { (keyword, mergedFilter, sort) ->
        taskUseCases.getFilteredTasks(keyword, mergedFilter, sort)
    }

    private val _dismissedSuggestionIds = MutableStateFlow<Set<String>>(emptySet())

    private val allTasksFlow: Flow<List<Task>> = taskUseCases.getFilteredTasks(
        "",
        TaskFilter(status = TaskFilter.Status.ALL),
        SortOrder.MANUAL
    )

    private val suggestionsFlow: Flow<List<com.example.pfptodo.domain.model.TaskSuggestion>> = combine(
        allTasksFlow,
        _dismissedSuggestionIds
    ) { allTasks, dismissed ->
        taskUseCases.getSmartSuggestions(allTasks, dismissed)
    }.distinctUntilChanged()

    private val smartFilterCountsFlow: Flow<SmartFilterCounts> = allTasksFlow.map { tasks ->
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val startOfToday = now.toLocalDate().atStartOfDay(zone).toInstant()
        val startOfTomorrow = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

        val activeTasks = tasks.filter { !it.isCompleted }
        val completedTasks = tasks.filter { it.isCompleted }
        val todayTasks = tasks.filter { it.dueDate != null && it.dueDate >= startOfToday && it.dueDate < startOfTomorrow }

        SmartFilterCounts(
            all = activeTasks.size,
            today = activeTasks.count { it.dueDate != null && it.dueDate >= startOfToday && it.dueDate < startOfTomorrow },
            todayTotal = todayTasks.size,
            todayCompleted = todayTasks.count { it.isCompleted },
            overdue = activeTasks.count { it.dueDate != null && it.dueDate.isBefore(startOfToday) },
            upcoming = activeTasks.count { it.dueDate != null && it.dueDate >= startOfTomorrow },
            highPriority = activeTasks.count { it.priority == com.example.pfptodo.domain.model.Priority.HIGH },
            recurring = activeTasks.count { it.recurrenceRule !is com.example.pfptodo.domain.model.RecurrenceRule.None },
            completed = completedTasks.size
        )
    }

    private data class FilterParams(val query: String, val parsedSearch: com.example.pfptodo.domain.model.ParsedSearchQuery, val filter: TaskFilter, val sortOrder: SortOrder)
    private data class SecondaryParams(
        val appTheme: AppTheme,
        val isSettingsSheetOpen: Boolean,
        val tags: List<Tag>,
        val categories: List<Category>,
        val counts: SmartFilterCounts,
        val suggestions: List<com.example.pfptodo.domain.model.TaskSuggestion>
    )

    private val secondaryParamsFlow = combine(
        combine(themeRepository.themeFlow, _isSettingsSheetOpen) { theme, open -> Pair(theme, open) },
        combine(availableTags, availableCategories) { tags, cats -> Pair(tags, cats) },
        combine(smartFilterCountsFlow, suggestionsFlow) { counts, sugg -> Pair(counts, sugg) }
    ) { (theme, open), (tags, cats), (counts, sugg) ->
        SecondaryParams(theme, open, tags, cats, counts, sugg)
    }

    val uiState: StateFlow<TaskListUiState> = combine(
        filteredTasksFlow,
        combine(_searchQuery, parsedSearchQueryFlow, _filter, _sortOrder) { q, p, f, s -> FilterParams(q, p, f, s) },
        secondaryParamsFlow
    ) { tasks, filterParams, secondary ->
        TaskListUiState(
            tasks = tasks,
            filter = filterParams.filter,
            sortOrder = filterParams.sortOrder,
            appTheme = secondary.appTheme,
            isSettingsSheetOpen = secondary.isSettingsSheetOpen,
            searchQuery = filterParams.query,
            parsedSearch = filterParams.parsedSearch,
            availableTags = secondary.tags,
            availableCategories = secondary.categories,
            smartFilterCounts = secondary.counts,
            suggestions = secondary.suggestions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskListUiState()
    )

    fun onDismissSuggestion(suggestionId: String) {
        _dismissedSuggestionIds.update { it + suggestionId }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(newFilter: TaskFilter) {
        _filter.value = newFilter
    }

    fun onTagFilterToggle(tagId: Long) {
        val currentTagIds = _filter.value.tagIds
        val newTagIds = if (tagId in currentTagIds) currentTagIds - tagId else currentTagIds + tagId
        _filter.value = _filter.value.copy(tagIds = newTagIds)
    }

    fun onClearTagFilter() {
        _filter.value = _filter.value.copy(tagIds = emptySet())
    }

    fun onToggleTagFilterMode() {
        val newMode = when (_filter.value.tagFilterMode) {
            TaskFilter.TagFilterMode.OR -> TaskFilter.TagFilterMode.AND
            TaskFilter.TagFilterMode.AND -> TaskFilter.TagFilterMode.OR
        }
        _filter.value = _filter.value.copy(tagFilterMode = newMode)
    }

    fun onSortOrderChange(newSortOrder: SortOrder) {
        _sortOrder.value = newSortOrder
    }

    fun onToggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskUseCases.toggleTaskCompletion(task)
        }
    }

    fun onSnoozeTask(task: Task, days: Long = 1) {
        viewModelScope.launch {
            dueDateMutex.withLock {
                taskUseCases.rescheduleTask.snoozeDays(task, days)
            }
        }
    }

    fun onDeleteTask(task: Task) {
        viewModelScope.launch {
            taskUseCases.deleteTask(task)
            reminderManager.cancelReminder(task.id)
        }
    }

    fun onMoveTask(idA: Long, idB: Long) {
        viewModelScope.launch {
            taskUseCases.swapTaskOrder(idA, idB)
        }
    }

    fun onSettingsClick() {
        _isSettingsSheetOpen.value = true
    }

    fun onDismissSettings() {
        _isSettingsSheetOpen.value = false
    }

    fun onThemeSelected(theme: AppTheme) {
        viewModelScope.launch {
            themeRepository.setTheme(theme)
        }
    }

    fun parseSmartInput(input: String): ParsedTask {
        return parseSmartTaskUseCase(input).parsedTask
    }

    fun onSaveSmartTask(parsedTask: ParsedTask) {
        if (parsedTask.title.isBlank()) return

        viewModelScope.launch {
            // 1. Resolve or create tags
            val tagIds = mutableListOf<Long>()
            for (tagName in parsedTask.tags) {
                val existingResult = tagUseCases.getTagByName(tagName)
                val existingTag = (existingResult as? Result.Success)?.data
                if (existingTag != null) {
                    tagIds.add(existingTag.id)
                } else {
                    val addResult = tagUseCases.addTag(Tag(name = tagName))
                    if (addResult is Result.Success) {
                        tagIds.add(addResult.data)
                    }
                }
            }

            // 2. Create and insert task
            val reminderInstant = if (parsedTask.hasExplicitReminder) parsedTask.dueInstant else null
            val newTask = Task(
                title = parsedTask.title,
                priority = parsedTask.priority,
                dueDate = parsedTask.dueInstant,
                recurrenceRule = parsedTask.recurrenceRule,
                recurrenceAnchor = if (parsedTask.recurrenceRule != com.example.pfptodo.domain.model.RecurrenceRule.None) parsedTask.dueInstant else null,
                reminderTime = reminderInstant
            )
            val insertResult = taskUseCases.addTask(newTask)
            if (insertResult is Result.Success) {
                val taskId = insertResult.data
                if (tagIds.isNotEmpty()) {
                    tagUseCases.setTaskTags(taskId, tagIds)
                }
                if (parsedTask.subtaskTitles.isNotEmpty()) {
                    taskUseCases.createSubtasksBatch(taskId, parsedTask.subtaskTitles)
                }
                if (reminderInstant != null) {
                    reminderManager.scheduleReminder(newTask.copy(id = taskId))
                }
            }
        }
    }

    fun onQuickSaveTask(title: String) {
        viewModelScope.launch {
            taskUseCases.addTask(Task(title = title))
        }
    }

    fun onCategoryFilterToggle(categoryId: Long) {
        val currentCategoryIds = _filter.value.categoryIds
        val newCategoryIds = if (categoryId in currentCategoryIds) currentCategoryIds - categoryId else currentCategoryIds + categoryId
        _filter.value = _filter.value.copy(categoryIds = newCategoryIds)
    }

    fun onClearCategoryFilter() {
        _filter.value = _filter.value.copy(categoryIds = emptySet())
    }

    fun onRestoreTaskDueDate(task: Task, originalDueDate: java.time.Instant?) {
        viewModelScope.launch {
            dueDateMutex.withLock {
                taskUseCases.rescheduleTask.setDueDate(task, originalDueDate)
            }
        }
    }

    fun onRemoveSearchChip(chip: com.example.pfptodo.domain.model.SearchFilterChip) {
        _searchQuery.value = ""
    }
}

data class SmartFilterCounts(
    val all: Int = 0,
    val today: Int = 0,
    val todayTotal: Int = 0,
    val todayCompleted: Int = 0,
    val overdue: Int = 0,
    val upcoming: Int = 0,
    val highPriority: Int = 0,
    val recurring: Int = 0,
    val completed: Int = 0
)

data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val filter: TaskFilter = TaskFilter(),
    val sortOrder: SortOrder = SortOrder.MANUAL,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val isSettingsSheetOpen: Boolean = false,
    val searchQuery: String = "",
    val parsedSearch: com.example.pfptodo.domain.model.ParsedSearchQuery = com.example.pfptodo.domain.model.ParsedSearchQuery(),
    val availableTags: List<Tag> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val smartFilterCounts: SmartFilterCounts = SmartFilterCounts(),
    val suggestions: List<com.example.pfptodo.domain.model.TaskSuggestion> = emptyList(),
    val error: String? = null
)
