package com.example.pfptodo.ui.screens.birthday

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pfptodo.R
import com.example.pfptodo.core.util.ContactBirthdayReader
import com.example.pfptodo.core.util.JalaliCalendarConverter
import com.example.pfptodo.domain.usecase.birthday.UpcomingBirthday
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.core.util.DigitNormalizer
import com.example.pfptodo.core.util.BirthdayDateConverter
import com.example.pfptodo.core.util.CalendarType
import com.example.pfptodo.ui.components.JalaliDatePickerDialog
import com.example.pfptodo.ui.theme.LocalCalendarType
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Benchmark-quality Birthday management screen matching Appllama design laws:
 * - Native Material 3 tokens, container hierarchy, and 8dp rhythm
 * - Composed empty state with actionable CTA
 * - Dual calendar representation (Gregorian + Solar Hijri)
 * - Safe-area compliant modal sheet with interactive date picking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayScreen(viewModel: BirthdayViewModel) {
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()
    val isEditorOpen by viewModel.isEditorOpen.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // System contact picker: grants access to exactly one chosen contact, zero dangerous permissions.
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val data = ContactBirthdayReader.read(context.contentResolver, uri)
            if (data != null) {
                viewModel.openEditorForNew(
                    ImportedContact(
                        name = data.name,
                        month = data.month,
                        day = data.day,
                        year = data.year,
                        contactId = data.contactId
                    )
                )
            } else {
                viewModel.openEditorForNew()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.birthday_title),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openEditorForNew() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.birthday_add)) },
                shape = MaterialTheme.shapes.large
            )
        }
    ) { padding ->
        if (upcoming.isEmpty()) {
            EmptyState(
                onAddBirthday = { viewModel.openEditorForNew() },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(upcoming, key = { it.birthday.id }) { item ->
                    BirthdayCard(
                        item = item,
                        onClick = { viewModel.openEditorForEdit(item.birthday) },
                        onDelete = { viewModel.delete(item.birthday) }
                    )
                }
            }
        }
    }

    if (isEditorOpen) {
        BirthdayEditorSheet(
            state = editorState,
            onNameChange = viewModel::onNameChange,
            onMonthChange = viewModel::onMonthChange,
            onDayChange = viewModel::onDayChange,
            onYearChange = viewModel::onYearChange,
            onNotesChange = viewModel::onNotesChange,
            onReminderTimeChange = viewModel::onReminderTimeChange,
            onNotifyDayBeforeChange = viewModel::onNotifyDayBeforeChange,
            onPickContact = { contactPicker.launch(null) },
            onSave = viewModel::save,
            onDismiss = viewModel::closeEditor
        )
    }
}

@Composable
private fun EmptyState(
    onAddBirthday: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Cake,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.birthday_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.birthday_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onAddBirthday,
            shape = MaterialTheme.shapes.small
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.birthday_add))
        }
    }
}

@Composable
private fun BirthdayCard(
    item: UpcomingBirthday,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val calendarType = LocalCalendarType.current
    val locale = Locale.getDefault()
    val primaryDate = AppDateFormat.date(item.occurrence, calendarType, locale)
    val otherType = if (calendarType == CalendarType.JALALI) CalendarType.GREGORIAN else CalendarType.JALALI
    val secondaryDate = AppDateFormat.date(item.occurrence, otherType, locale)

    val initial = item.birthday.name.firstOrNull()?.uppercaseChar()?.toString() ?: ""

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (item.isToday) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with initial or cake icon
            Surface(
                shape = CircleShape,
                color = if (item.isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (initial.isNotBlank()) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Cake,
                            contentDescription = null,
                            tint = if (item.isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.birthday.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$primaryDate · $secondaryDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.age != null && item.age > 0) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = AppDateFormat.localizeDigits(stringResource(R.string.birthday_turns_age, item.age), Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Days countdown badge
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    item.isToday -> MaterialTheme.colorScheme.primary
                    item.daysUntil == 1L -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Text(
                    text = when (item.daysUntil) {
                        0L -> stringResource(R.string.birthday_today)
                        1L -> stringResource(R.string.birthday_tomorrow)
                        else -> AppDateFormat.localizeDigits(stringResource(R.string.birthday_in_days, item.daysUntil), Locale.getDefault())
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        item.isToday -> MaterialTheme.colorScheme.onPrimary
                        item.daysUntil == 1L -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Delete button with 40dp target
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayEditorSheet(
    state: BirthdayEditorState,
    onNameChange: (String) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onYearChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onReminderTimeChange: (Int) -> Unit,
    onNotifyDayBeforeChange: (Boolean) -> Unit,
    onPickContact: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val calendarType = LocalCalendarType.current
    val isJalali = calendarType == CalendarType.JALALI
    val referenceGregorianYear = java.time.LocalDate.now().year
    val referenceJalaliYear = AppDateFormat.year(java.time.LocalDate.now(), CalendarType.JALALI)
    // The record is stored as Gregorian; in Jalali mode it is shown (and edited) as Jalali.
    val shown = if (isJalali) {
        BirthdayDateConverter.gregorianToJalali(state.month, state.day, state.birthYear, referenceGregorianYear)
    } else {
        BirthdayDateConverter.Fields(state.month, state.day, state.birthYear)
    }
    // Applies a month/day/year typed in the active calendar back to the stored Gregorian fields.
    val applyShown: (Int, Int, Int?) -> Unit = { m, d, y ->
        if (isJalali) {
            val g = BirthdayDateConverter.jalaliToGregorian(m, d, y, referenceJalaliYear)
            onMonthChange(g.month); onDayChange(g.day); onYearChange(g.year?.toString() ?: "")
        } else {
            onMonthChange(m); onDayChange(d); onYearChange(y?.toString() ?: "")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (state.id == 0L) R.string.birthday_add else R.string.birthday_edit
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Contact Picker Button
            OutlinedButton(
                onClick = onPickContact,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.birthday_pick_contact))
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.birthday_name_label)) },
                isError = state.error == "name_blank",
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )

            // Calendar Picker Button
            OutlinedButton(
                onClick = { showDatePickerDialog = true },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    AppDateFormat.localizeDigits("${shown.month}/${shown.day}" + (shown.year?.let { " ($it)" } ?: ""), Locale.getDefault()) +
                        "  ·  " + stringResource(if (isJalali) R.string.calendar_jalali else R.string.calendar_gregorian)
                )
            }

            // Date Inputs Row (Month, Day, Year)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = shown.month.toString(),
                    onValueChange = { text ->
                        DigitNormalizer.toAsciiDigits(text).toIntOrNull()?.takeIf { it in 1..12 }
                            ?.let { applyShown(it, shown.day, shown.year) }
                    },
                    label = { Text(stringResource(R.string.birthday_month_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = shown.day.toString(),
                    onValueChange = { text ->
                        DigitNormalizer.toAsciiDigits(text).toIntOrNull()?.takeIf { it in 1..31 }
                            ?.let { applyShown(shown.month, it, shown.year) }
                    },
                    label = { Text(stringResource(R.string.birthday_day_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = shown.year?.toString() ?: "",
                    onValueChange = { text ->
                        val digits = DigitNormalizer.toAsciiDigits(text).filter { it.isDigit() }.take(4)
                        if (digits.isEmpty()) {
                            applyShown(shown.month, shown.day, null)
                        } else if (digits.length == 4) {
                            applyShown(shown.month, shown.day, digits.toInt())
                        }
                    },
                    label = { Text(stringResource(R.string.birthday_year_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.birthday_notes_label)) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )

            // Reminder Time Selection
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.birthday_reminder_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = AppDateFormat.localizeDigits(
                                String.format(
                                    Locale.getDefault(),
                                    "%02d:%02d",
                                    state.reminderMinutesOfDay / 60,
                                    state.reminderMinutesOfDay % 60
                                ),
                                Locale.getDefault()
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(9 * 60 to "09:00", 12 * 60 to "12:00", 18 * 60 to "18:00").forEach { (mins, label) ->
                            FilterChip(
                                selected = state.reminderMinutesOfDay == mins,
                                onClick = { onReminderTimeChange(mins) },
                                label = { Text(AppDateFormat.localizeDigits(label, Locale.getDefault())) }
                            )
                        }
                    }
                }
            }

            // Remind Day Before Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.birthday_day_before_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = state.notifyOnDayBefore,
                    onCheckedChange = onNotifyDayBeforeChange
                )
            }

            if (state.error != null) {
                Text(
                    text = stringResource(errorMessageRes(state.error)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    enabled = state.name.isNotBlank(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showDatePickerDialog && isJalali) {
        val initial = try {
            if (shown.year != null) {
                JalaliCalendarConverter.jalaliToGregorian(shown.year, shown.month, shown.day)
            } else {
                JalaliCalendarConverter.jalaliToGregorian(referenceJalaliYear, shown.month, shown.day)
            }
        } catch (e: Exception) {
            java.time.LocalDate.now()
        }
        JalaliDatePickerDialog(
            initialDate = initial,
            onDismiss = { showDatePickerDialog = false },
            onConfirm = { picked ->
                onMonthChange(picked.monthValue)
                onDayChange(picked.dayOfMonth)
                onYearChange(picked.year.toString())
                showDatePickerDialog = false
            }
        )
    } else if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        onMonthChange(cal.get(Calendar.MONTH) + 1)
                        onDayChange(cal.get(Calendar.DAY_OF_MONTH))
                        onYearChange(cal.get(Calendar.YEAR).toString())
                    }
                    showDatePickerDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun errorMessageRes(token: String): Int = when (token) {
    "name_blank" -> R.string.birthday_err_name_blank
    "bad_month" -> R.string.birthday_err_bad_month
    "bad_day" -> R.string.birthday_err_bad_day
    "bad_year" -> R.string.birthday_err_bad_year
    "future_date" -> R.string.birthday_err_future_date
    else -> R.string.birthday_err_save_failed
}
