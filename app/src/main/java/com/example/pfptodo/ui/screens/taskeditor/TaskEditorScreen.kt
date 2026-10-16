package com.example.pfptodo.ui.screens.taskeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.ui.components.PRESET_TAG_COLORS
import com.example.pfptodo.ui.components.TagChip
import com.example.pfptodo.ui.components.TagEditorDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.AccessTime
import com.example.pfptodo.core.util.getDisplayName
import java.time.Instant
import java.text.SimpleDateFormat
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.core.util.CalendarType
import com.example.pfptodo.ui.components.JalaliDatePickerDialog
import com.example.pfptodo.ui.theme.LocalCalendarType
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    viewModel: TaskEditorViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBack()
        }
    }

    // Localized message wins; the raw detail is only a fallback.
    val saveErrorText = uiState.saveErrorRes?.let { stringResource(it) } ?: uiState.saveError
    LaunchedEffect(saveErrorText) {
        saveErrorText?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    val calendarType = LocalCalendarType.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // The task loads asynchronously when editing, so point the picker at its date each time it
    // opens. DatePicker works in UTC midnight, hence the local-date -> UTC conversion.
    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            uiState.dueDate?.let { due ->
                datePickerState.selectedDateMillis = due.atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }
        }
    }
    var showAddTagDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) stringResource(R.string.edit_task_title) else stringResource(R.string.create_task_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                if (uiState.title.isNotBlank()) {
                                    viewModel.onSave()
                                }
                            },
                            enabled = uiState.title.isNotBlank() && !uiState.isSaving
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onTitleChange(it) },
                label = { Text(stringResource(R.string.task_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text(stringResource(R.string.description_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.onNotesChange(it) },
                label = { Text(stringResource(R.string.task_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Text(stringResource(R.string.priority_label), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(
                        selected = uiState.priority == p,
                        onClick = { viewModel.onPriorityChange(p) },
                        label = {
                            Text(
                                when (p) {
                                    Priority.HIGH -> stringResource(R.string.priority_high)
                                    Priority.MEDIUM -> stringResource(R.string.priority_medium)
                                    Priority.LOW -> stringResource(R.string.priority_low)
                                }
                            )
                        }
                    )
                }
            }

            // Category section
            if (uiState.availableCategories.isNotEmpty()) {
                val context = LocalContext.current
                Text(stringResource(R.string.organization_label), style = MaterialTheme.typography.titleMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCategoryId == null,
                            onClick = { viewModel.onSelectCategory(null) },
                            label = { Text(stringResource(R.string.none_label)) }
                        )
                    }
                    items(uiState.availableCategories, key = { it.id }) { cat ->
                        val isSelected = uiState.selectedCategoryId == cat.id
                        val color = try {
                            Color(android.graphics.Color.parseColor(cat.color))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onSelectCategory(if (isSelected) null else cat.id) },
                            label = { Text(cat.getDisplayName(context)) },
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

            // Tags section
            Text(stringResource(R.string.tags_label), style = MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uiState.availableTags, key = { it.id }) { tag ->
                    val isSelected = tag.id in uiState.selectedTagIds
                    TagChip(
                        tag = tag,
                        isSelected = isSelected,
                        onClick = { viewModel.onToggleTag(tag.id) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { showAddTagDialog = true },
                        label = { Text(stringResource(R.string.add_tag)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Checklist section
            var newSubtaskInput by remember { mutableStateOf("") }
            Text(stringResource(R.string.checklist_title), style = MaterialTheme.typography.titleMedium)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.subtasks.forEachIndexed { index, subtaskTitle ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = subtaskTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.onRemoveSubtask(index) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_delete),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubtaskInput,
                        onValueChange = { newSubtaskInput = it },
                        placeholder = { Text(stringResource(R.string.checklist_add_item_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (newSubtaskInput.isNotBlank()) {
                                    viewModel.onAddSubtask(newSubtaskInput)
                                    newSubtaskInput = ""
                                }
                            }
                        ),
                        trailingIcon = {
                            if (newSubtaskInput.isNotBlank()) {
                                IconButton(onClick = {
                                    viewModel.onAddSubtask(newSubtaskInput)
                                    newSubtaskInput = ""
                                }) {
                                    Icon(
                                        Icons.Default.AddCircle,
                                        contentDescription = stringResource(R.string.add_subtask),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )
                }
            }

            Text(stringResource(R.string.due_date_label), style = MaterialTheme.typography.titleMedium)
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.due_date_label)) },
                supportingContent = { Text(uiState.dueDate?.let { AppDateFormat.dateTime(it, java.time.ZoneId.systemDefault(), calendarType, Locale.getDefault()) } ?: stringResource(R.string.no_date_set)) },
                leadingContent = { Icon(Icons.Default.DateRange, null) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.set_label)) }
                        if (uiState.dueDate != null) {
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Default.AccessTime, contentDescription = stringResource(R.string.cd_set_time), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { viewModel.onDueDateChange(null) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_due_date), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.recurrence_label)) },
                supportingContent = { Text(recurrenceLabel(uiState.recurrenceRule)) },
                leadingContent = { Icon(Icons.Default.Repeat, null) },
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    var showIntervalDialog by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.set_label)) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(
                                "None" to stringResource(R.string.none_label),
                                "DAILY" to stringResource(R.string.recurrence_daily),
                                "WEEKLY" to stringResource(R.string.recurrence_weekly),
                                "MONTHLY" to stringResource(R.string.recurrence_monthly),
                                "YEARLY" to stringResource(R.string.recurrence_yearly)
                            ).forEach { (rule, label) ->
                                DropMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.onRecurrenceChange(com.example.pfptodo.domain.model.RecurrenceRule.fromString(rule))
                                        expanded = false
                                    }
                                )
                            }
                            DropMenuItem(
                                text = { Text(stringResource(R.string.recurrence_custom_interval)) },
                                onClick = {
                                    expanded = false
                                    showIntervalDialog = true
                                }
                            )
                        }
                    }

                    if (showIntervalDialog) {
                        val initialDays =
                            (uiState.recurrenceRule as? com.example.pfptodo.domain.model.RecurrenceRule.Interval)?.days ?: 2
                        IntervalInputDialog(
                            initialDays = initialDays,
                            onDismiss = { showIntervalDialog = false },
                            onConfirm = { days ->
                                viewModel.onRecurrenceChange(
                                    com.example.pfptodo.domain.model.RecurrenceRule.Interval(days)
                                )
                                showIntervalDialog = false
                            }
                        )
                    }
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.reminder_label)) },
                supportingContent = { Text(uiState.reminderTime?.let { AppDateFormat.dateTime(it, java.time.ZoneId.systemDefault(), calendarType, Locale.getDefault()) } ?: stringResource(R.string.not_scheduled)) },
                leadingContent = { Icon(Icons.Default.Notifications, null) },
                trailingContent = {
                    Switch(
                        checked = uiState.reminderTime != null,
                        onCheckedChange = { checked ->
                            viewModel.onReminderChange(if (checked) (uiState.dueDate ?: Instant.now()) else null)
                        }
                    )
                }
            )

            if (showDatePicker && calendarType == CalendarType.JALALI) {
                JalaliDatePickerDialog(
                    initialDate = (uiState.dueDate ?: Instant.now()).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                    onDismiss = { showDatePicker = false },
                    onConfirm = { picked ->
                        val existingDue = uiState.dueDate?.atZone(java.time.ZoneId.systemDefault())
                        val newDue = picked
                            .atTime(existingDue?.hour ?: 9, existingDue?.minute ?: 0)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                        viewModel.onDueDateChange(newDue)
                        showDatePicker = false
                        showTimePicker = true
                    }
                )
            } else if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                                cal.timeInMillis = millis
                                val targetCal = Calendar.getInstance()
                                val existingDue = uiState.dueDate?.atZone(java.time.ZoneId.systemDefault())
                                targetCal.set(
                                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                                    existingDue?.hour ?: 9, existingDue?.minute ?: 0, 0
                                )
                                targetCal.set(Calendar.MILLISECOND, 0)
                                viewModel.onDueDateChange(targetCal.toInstant())
                            }
                            showDatePicker = false
                            showTimePicker = true
                        }) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                val currentDue = uiState.dueDate?.atZone(java.time.ZoneId.systemDefault())
                val timePickerState = rememberTimePickerState(
                    initialHour = currentDue?.hour ?: 9,
                    initialMinute = currentDue?.minute ?: 0,
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val cal = Calendar.getInstance()
                            val currentMillis = uiState.dueDate?.toEpochMilli() ?: System.currentTimeMillis()
                            cal.timeInMillis = currentMillis
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            viewModel.onDueDateChange(cal.toInstant())
                            showTimePicker = false
                        }) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
                    },
                    text = {
                        TimePicker(state = timePickerState)
                    }
                )
            }

            if (showAddTagDialog) {
                TagEditorDialog(
                    title = stringResource(R.string.new_tag),
                    initialName = "",
                    initialColor = PRESET_TAG_COLORS.first(),
                    existingTags = uiState.availableTags,
                    onDismiss = { showAddTagDialog = false },
                    onConfirm = { name, color ->
                        viewModel.onCreateTag(name, color)
                        showAddTagDialog = false
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DropMenuItem(text: @Composable () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(text = text, onClick = onClick)
}

@Composable
private fun IntervalInputDialog(
    initialDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialDays.coerceAtLeast(1).toString()) }
    // `it.isDigit()` accepts Persian/Arabic-Indic digits too, but toIntOrNull() only
    // parses ASCII — normalize first so the Confirm button enables for fa digits.
    val asciiText = text.map { ch ->
        when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            else -> ch
        }
    }.joinToString("")
    val days = asciiText.toIntOrNull()
    val isValid = days != null && days in 1..365

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurrence_custom_interval)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(3) },
                label = { Text(stringResource(R.string.recurrence_interval_days_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = text.isNotEmpty() && !isValid,
                supportingText = {
                    Text(stringResource(R.string.recurrence_interval_hint))
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { days?.let(onConfirm) },
                enabled = isValid
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun recurrenceLabel(rule: com.example.pfptodo.domain.model.RecurrenceRule): String {
    return when (rule) {
        com.example.pfptodo.domain.model.RecurrenceRule.None -> stringResource(R.string.none_label)
        com.example.pfptodo.domain.model.RecurrenceRule.Daily -> stringResource(R.string.recurrence_daily)
        com.example.pfptodo.domain.model.RecurrenceRule.Weekly -> stringResource(R.string.recurrence_weekly)
        com.example.pfptodo.domain.model.RecurrenceRule.Monthly -> stringResource(R.string.recurrence_monthly)
        com.example.pfptodo.domain.model.RecurrenceRule.Yearly -> stringResource(R.string.recurrence_yearly)
        is com.example.pfptodo.domain.model.RecurrenceRule.Interval ->
            AppDateFormat.localizeDigits(stringResource(R.string.recurrence_interval, rule.days), Locale.getDefault())
    }
}
