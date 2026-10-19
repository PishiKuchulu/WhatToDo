package com.example.pfptodo.ui.screens.tasklist

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.ParsedTask
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.model.Tag
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.ui.components.ReorderableLazyColumn
import com.example.pfptodo.ui.components.SettingsBottomSheet
import com.example.pfptodo.ui.components.SmartTaskPreviewCard
import com.example.pfptodo.ui.components.SwipeableTaskItem
import com.example.pfptodo.ui.components.TagChip
import com.example.pfptodo.ui.components.VoiceInputBottomSheet
import com.example.pfptodo.ui.navigation.AppActions
import com.example.pfptodo.ui.screens.taskeditor.TaskPrefill

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.core.util.getDisplayName
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onTaskClick: (Long) -> Unit,
    onAddTask: () -> Unit,
    onAddTaskWithPrefill: (TaskPrefill) -> Unit = { onAddTask() },
    onOpenMatrix: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showVoiceInput by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var quickAddText by remember { mutableStateOf("") }
    var parsedQuickTask by remember { mutableStateOf<ParsedTask?>(null) }

    // Local search text: keystrokes only recompose the search field. The ViewModel
    // (whose uiState rebuild recomposes the whole screen) is updated debounced.
    // The key keeps the field in sync when the query changes externally
    // (e.g. chip removal or the empty-state "clear filters" action).
    var searchText by remember(uiState.searchQuery) { mutableStateOf(uiState.searchQuery) }
    LaunchedEffect(searchText) {
        delay(300)
        if (searchText != viewModel.uiState.value.searchQuery) {
            viewModel.onSearchQueryChange(searchText)
        }
    }

    val taskSnoozedMsg = stringResource(R.string.task_snoozed)
    val taskDoneMsg = stringResource(R.string.task_completed_undo)
    val undoLabel = stringResource(R.string.undo)

    // Launcher shortcut / widget "voice" action: open the voice sheet once, then reset the request.
    val voiceRequested by AppActions.openVoiceInput.collectAsStateWithLifecycle()
    LaunchedEffect(voiceRequested) {
        if (voiceRequested) {
            showVoiceInput = true
            AppActions.openVoiceInput.value = false
        }
    }

    // Debounced smart-parse: the heavy fa/en regex parsers (~700 lines) must not run
    // on the main thread for every keystroke. After a 250ms pause in typing the
    // parse is offloaded to Dispatchers.Default; the preview card updates when it lands.
    LaunchedEffect(quickAddText) {
        if (quickAddText.isBlank()) {
            parsedQuickTask = null
            return@LaunchedEffect
        }
        delay(250)
        val input = quickAddText
        parsedQuickTask = withContext(Dispatchers.Default) {
            viewModel.parseSmartInput(input)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.todo_title),
                            fontWeight = FontWeight.Black
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenMatrix) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = stringResource(R.string.matrix_title)
                            )
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                when(order) {
                                                    SortOrder.MANUAL -> stringResource(R.string.sort_manual)
                                                    SortOrder.CREATION_DATE -> stringResource(R.string.sort_creation_date)
                                                    SortOrder.DUE_DATE -> stringResource(R.string.sort_due_date)
                                                    SortOrder.PRIORITY -> stringResource(R.string.sort_priority)
                                                }
                                            )
                                        },
                                        onClick = {
                                            viewModel.onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (uiState.sortOrder == order) Icon(Icons.Default.Check, null)
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = viewModel::onSettingsClick) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = stringResource(R.string.theme_settings)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchText,
                            onQueryChange = { searchText = it },
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchText.isNotEmpty()) {
                                    IconButton(onClick = { searchText = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                                    }
                                }
                            }
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {}

                // Natural Language Search Intent Chips
                AnimatedVisibility(
                    visible = uiState.parsedSearch.chips.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        items(uiState.parsedSearch.chips, key = { it.id }) { chip ->
                            val labelText = if (chip.labelRes != 0) stringResource(chip.labelRes) else (chip.customLabel ?: "")
                            InputChip(
                                selected = true,
                                onClick = { viewModel.onRemoveSearchChip(chip) },
                                label = { Text(labelText, style = MaterialTheme.typography.labelMedium) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.cancel),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }

                SmartFilterRow(
                    selectedFilter = uiState.filter,
                    counts = uiState.smartFilterCounts,
                    onFilterChange = viewModel::onFilterChange
                )

                if (uiState.availableTags.isNotEmpty()) {
                    TagFilterRow(
                        tags = uiState.availableTags,
                        selectedTagIds = uiState.filter.tagIds,
                        tagFilterMode = uiState.filter.tagFilterMode,
                        onTagToggle = viewModel::onTagFilterToggle,
                        onClearTags = viewModel::onClearTagFilter,
                        onToggleMode = viewModel::onToggleTagFilterMode
                    )
                }

                if (uiState.availableCategories.isNotEmpty()) {
                    CategoryFilterRow(
                        categories = uiState.availableCategories,
                        selectedCategoryIds = uiState.filter.categoryIds,
                        onCategoryToggle = viewModel::onCategoryFilterToggle,
                        onClearCategories = viewModel::onClearCategoryFilter
                    )
                }

                // Smart Quick Add Field
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    OutlinedTextField(
                        value = quickAddText,
                        onValueChange = { quickAddText = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.smart_add_hint),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (quickAddText.isNotEmpty()) {
                                    IconButton(onClick = {
                                        quickAddText = ""
                                        parsedQuickTask = null
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { showVoiceInput = true }) {
                                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.cd_voice_task), modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                parsedQuickTask?.let { currentTask ->
                                    if (currentTask.title.isNotBlank()) {
                                        viewModel.onSaveSmartTask(currentTask)
                                        quickAddText = ""
                                        parsedQuickTask = null
                                        focusManager.clearFocus()
                                    }
                                }
                            }
                        ),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Animated Smart Preview Card
                    AnimatedVisibility(
                        visible = parsedQuickTask != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        parsedQuickTask?.let { currentTask ->
                            Spacer(Modifier.height(8.dp))
                            SmartTaskPreviewCard(
                                parsedTask = currentTask,
                                onTitleChange = { newTitle ->
                                    parsedQuickTask = currentTask.copy(title = newTitle)
                                },
                                onRemoveDate = {
                                    parsedQuickTask = currentTask.copy(date = null, dueInstant = null)
                                },
                                onRemoveTime = {
                                    val newInstant = currentTask.date?.atTime(9, 0)?.atZone(java.time.ZoneId.systemDefault())?.toInstant()
                                    parsedQuickTask = currentTask.copy(time = null, dueInstant = newInstant)
                                },
                                onRemovePriority = {
                                    parsedQuickTask = currentTask.copy(priority = Priority.MEDIUM)
                                },
                                onRemoveTag = { tagToRemove ->
                                    parsedQuickTask = currentTask.copy(tags = currentTask.tags.filter { it != tagToRemove })
                                },
                                onRemoveRecurrence = {
                                    parsedQuickTask = currentTask.copy(recurrenceRule = RecurrenceRule.None)
                                },
                                onConfirm = {
                                    viewModel.onSaveSmartTask(currentTask)
                                    quickAddText = ""
                                    parsedQuickTask = null
                                    focusManager.clearFocus()
                                },
                                onOpenEditor = {
                                    // Hand what was typed/parsed over to the editor instead of dropping it.
                                    onAddTaskWithPrefill(TaskPrefill.from(currentTask))
                                    quickAddText = ""
                                    parsedQuickTask = null
                                    focusManager.clearFocus()
                                },
                                onCancel = {
                                    quickAddText = ""
                                    parsedQuickTask = null
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }
                }

                // Smart Suggestions Carousel
                SmartSuggestionsCarousel(
                    suggestions = uiState.suggestions,
                    onDismiss = viewModel::onDismissSuggestion,
                    onAction = { action ->
                        when (action) {
                            is com.example.pfptodo.domain.model.SuggestionAction.OpenTask -> onTaskClick(action.taskId)
                            is com.example.pfptodo.domain.model.SuggestionAction.OpenTaskEditor -> onTaskClick(action.taskId)
                            is com.example.pfptodo.domain.model.SuggestionAction.FilterOverdue -> viewModel.onFilterChange(TaskFilter(status = TaskFilter.Status.OVERDUE))
                            is com.example.pfptodo.domain.model.SuggestionAction.FilterToday -> viewModel.onFilterChange(TaskFilter(dateFilter = TaskFilter.DateFilter.TODAY))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(text = stringResource(R.string.add_task_button), fontWeight = FontWeight.Bold) },
                expanded = scrollBehavior.state.collapsedFraction < 0.5f
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = uiState.tasks.isEmpty(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "MainContentTransition"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyState(
                        isFiltered = uiState.searchQuery.isNotBlank() ||
                            uiState.filter != TaskFilter(),
                        onClearFilter = {
                            viewModel.onSearchQueryChange("")
                            viewModel.onFilterChange(TaskFilter())
                        },
                        onAddTask = onAddTask,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (uiState.sortOrder == SortOrder.MANUAL) {
                    ReorderableLazyColumn(
                        items = uiState.tasks,
                        onReorder = { from, to ->
                            val itemA = uiState.tasks[from]
                            val itemB = uiState.tasks[to]
                            viewModel.onMoveTask(itemA.id, itemB.id)
                        },
                        modifier = Modifier.fillMaxSize(),
                        key = { it.id }
                    ) { task, isDragging ->
                        val taskCategory = uiState.availableCategories.find { it.id == task.categoryId }
                        SwipeableTaskItem(
                            task = task,
                            category = taskCategory,
                            onToggleCompletion = {
                                viewModel.onToggleTaskCompletion(task)
                                if (!task.isCompleted) {
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = taskDoneMsg,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.onToggleTaskCompletion(task.copy(isCompleted = true))
                                        }
                                    }
                                }
                            },
                            onSnooze = {
                                val originalDueDate = task.dueDate
                                viewModel.onSnoozeTask(task, 1)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = taskSnoozedMsg,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.onRestoreTaskDueDate(task, originalDueDate)
                                    }
                                }
                            },
                            onDelete = { viewModel.onDeleteTask(task) },
                            onClick = { onTaskClick(task.id) }
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.tasks, key = { it.id }) { task ->
                            val taskCategory = uiState.availableCategories.find { it.id == task.categoryId }
                            SwipeableTaskItem(
                                task = task,
                                category = taskCategory,
                                onToggleCompletion = {
                                    viewModel.onToggleTaskCompletion(task)
                                    if (!task.isCompleted) {
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            val result = snackbarHostState.showSnackbar(
                                                message = taskDoneMsg,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.onToggleTaskCompletion(task.copy(isCompleted = true))
                                            }
                                        }
                                    }
                                },
                                onSnooze = {
                                    val originalDueDate = task.dueDate
                                    viewModel.onSnoozeTask(task, 1)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = taskSnoozedMsg,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.onRestoreTaskDueDate(task, originalDueDate)
                                        }
                                    }
                                },
                                onDelete = { viewModel.onDeleteTask(task) },
                                onClick = { onTaskClick(task.id) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isSettingsSheetOpen) {
            SettingsBottomSheet(
                selectedTheme = uiState.appTheme,
                onThemeSelected = viewModel::onThemeSelected,
                onDismiss = viewModel::onDismissSettings
            )
        }

        if (showVoiceInput) {
            VoiceInputBottomSheet(
                onResult = { title ->
                    viewModel.onQuickSaveTask(title)
                    showVoiceInput = false
                },
                onParsedResult = { parsedTask ->
                    viewModel.onSaveSmartTask(parsedTask)
                    showVoiceInput = false
                },
                onOpenEditorWithTask = { parsedTask ->
                    onAddTaskWithPrefill(TaskPrefill.from(parsedTask))
                    showVoiceInput = false
                },
                onDismiss = { showVoiceInput = false }
            )
        }
    }
}

private enum class SmartFilterType {
    ALL, TODAY, TOMORROW, THIS_WEEK, THIS_MONTH, OVERDUE, UPCOMING,
    HIGH_PRIORITY, MEDIUM_PRIORITY, LOW_PRIORITY, RECURRING, COMPLETED
}

@Composable
fun SmartFilterRow(
    selectedFilter: TaskFilter,
    counts: SmartFilterCounts,
    onFilterChange: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentType = when {
        selectedFilter.status == TaskFilter.Status.COMPLETED -> SmartFilterType.COMPLETED
        selectedFilter.status == TaskFilter.Status.OVERDUE -> SmartFilterType.OVERDUE
        selectedFilter.dateFilter == TaskFilter.DateFilter.TODAY -> SmartFilterType.TODAY
        selectedFilter.dateFilter == TaskFilter.DateFilter.TOMORROW -> SmartFilterType.TOMORROW
        selectedFilter.dateFilter == TaskFilter.DateFilter.THIS_WEEK -> SmartFilterType.THIS_WEEK
        selectedFilter.dateFilter == TaskFilter.DateFilter.THIS_MONTH -> SmartFilterType.THIS_MONTH
        selectedFilter.dateFilter == TaskFilter.DateFilter.UPCOMING -> SmartFilterType.UPCOMING
        selectedFilter.priorities == setOf(Priority.HIGH) -> SmartFilterType.HIGH_PRIORITY
        selectedFilter.priorities == setOf(Priority.MEDIUM) -> SmartFilterType.MEDIUM_PRIORITY
        selectedFilter.priorities == setOf(Priority.LOW) -> SmartFilterType.LOW_PRIORITY
        selectedFilter.isRecurringOnly -> SmartFilterType.RECURRING
        else -> SmartFilterType.ALL
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = currentType == SmartFilterType.ALL,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ALL,
                            dateFilter = TaskFilter.DateFilter.ALL,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.smart_filter_all)) },
                trailingIcon = {
                    if (counts.all > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(AppDateFormat.localizeDigits(counts.all.toString(), Locale.getDefault()))
                        }
                    }
                }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.TODAY,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.TODAY,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.smart_filter_today)) },
                trailingIcon = {
                    if (counts.today > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(AppDateFormat.localizeDigits(counts.today.toString(), Locale.getDefault()))
                        }
                    }
                }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.OVERDUE,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.OVERDUE,
                            dateFilter = TaskFilter.DateFilter.ALL,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.smart_filter_overdue)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                trailingIcon = {
                    if (counts.overdue > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(AppDateFormat.localizeDigits(counts.overdue.toString(), Locale.getDefault()), color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.TOMORROW,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.TOMORROW,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.search_chip_tomorrow)) }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.THIS_WEEK,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.THIS_WEEK,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.search_chip_this_week)) }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.THIS_MONTH,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.THIS_MONTH,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.search_chip_this_month)) }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.UPCOMING,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.UPCOMING,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.smart_filter_upcoming)) },
                trailingIcon = {
                    if (counts.upcoming > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(AppDateFormat.localizeDigits(counts.upcoming.toString(), Locale.getDefault()))
                        }
                    }
                }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.HIGH_PRIORITY,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.ALL,
                            priorities = setOf(Priority.HIGH),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.smart_filter_priority_high)) },
                trailingIcon = {
                    if (counts.highPriority > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(AppDateFormat.localizeDigits(counts.highPriority.toString(), Locale.getDefault()))
                        }
                    }
                }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.MEDIUM_PRIORITY,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.ALL,
                            priorities = setOf(Priority.MEDIUM),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.smart_filter_priority_medium)) }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.LOW_PRIORITY,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.ALL,
                            priorities = setOf(Priority.LOW),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.search_chip_low_priority)) }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.RECURRING,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.ACTIVE,
                            dateFilter = TaskFilter.DateFilter.ALL,
                            priorities = emptySet(),
                            isRecurringOnly = true
                        )
                    )
                },
                label = { Text(stringResource(R.string.smart_filter_recurring)) },
                trailingIcon = {
                    if (counts.recurring > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(AppDateFormat.localizeDigits(counts.recurring.toString(), Locale.getDefault()))
                        }
                    }
                }
            )
        }

        item {
            FilterChip(
                selected = currentType == SmartFilterType.COMPLETED,
                onClick = {
                    onFilterChange(
                        selectedFilter.copy(
                            status = TaskFilter.Status.COMPLETED,
                            dateFilter = TaskFilter.DateFilter.ALL,
                            priorities = emptySet(),
                            isRecurringOnly = false
                        )
                    )
                },
                label = { Text(stringResource(R.string.status_completed)) },
                trailingIcon = {
                    if (counts.completed > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(AppDateFormat.localizeDigits(counts.completed.toString(), Locale.getDefault()))
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun TagFilterRow(
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    tagFilterMode: TaskFilter.TagFilterMode,
    onTagToggle: (Long) -> Unit,
    onClearTags: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = selectedTagIds.isEmpty(),
                onClick = onClearTags,
                label = { Text(stringResource(R.string.tag_filter_all)) }
            )
        }
        if (selectedTagIds.size > 1) {
            item {
                SuggestionChip(
                    onClick = onToggleMode,
                    label = {
                        Text(
                            stringResource(
                                if (tagFilterMode == TaskFilter.TagFilterMode.OR) R.string.tag_filter_mode_or
                                else R.string.tag_filter_mode_and
                            )
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
        items(tags, key = { it.id }) { tag ->
            val isSelected = tag.id in selectedTagIds
            TagChip(
                tag = tag,
                isSelected = isSelected,
                onClick = { onTagToggle(tag.id) }
            )
        }
    }
}

@Composable
fun CategoryFilterRow(
    categories: List<Category>,
    selectedCategoryIds: Set<Long>,
    onCategoryToggle: (Long) -> Unit,
    onClearCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = selectedCategoryIds.isEmpty(),
                onClick = onClearCategories,
                label = { Text(stringResource(R.string.category_all)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        items(categories, key = { it.id }) { category ->
            val isSelected = category.id in selectedCategoryIds
            val color = try {
                Color(android.graphics.Color.parseColor(category.color))
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }
            FilterChip(
                selected = isSelected,
                onClick = { onCategoryToggle(category.id) },
                label = { Text(category.getDisplayName(context)) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, CircleShape)
                    )
                }
            )
        }
    }
}

@Composable
fun EmptyState(
    isFiltered: Boolean = false,
    onClearFilter: () -> Unit = {},
    onAddTask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFiltered) Icons.Default.FilterAlt else Icons.Default.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.no_tasks_found_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isFiltered) {
            OutlinedButton(
                onClick = onClearFilter,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.status_all))
            }
        } else {
            Button(
                onClick = onAddTask,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.add_task_button))
            }
        }
    }
}
