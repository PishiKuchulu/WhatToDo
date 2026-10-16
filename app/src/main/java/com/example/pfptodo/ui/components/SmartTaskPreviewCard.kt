package com.example.pfptodo.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.domain.model.ParsedTask
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

@Composable
fun SmartTaskPreviewCard(
    parsedTask: ParsedTask,
    onTitleChange: (String) -> Unit,
    onRemoveDate: () -> Unit,
    onRemoveTime: () -> Unit,
    onRemovePriority: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onRemoveRecurrence: () -> Unit,
    onConfirm: () -> Unit,
    onOpenEditor: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.smart_preview_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Editable Title Field
            OutlinedTextField(
                value = parsedTask.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onConfirm() }
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                placeholder = { Text(stringResource(R.string.task_title)) }
            )

            // Extracted Attribute Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date chip
                if (parsedTask.date != null) {
                    val previewCalendar = com.example.pfptodo.ui.theme.LocalCalendarType.current
                    InputChip(
                        selected = true,
                        onClick = onRemoveDate,
                        label = { Text(com.example.pfptodo.core.util.AppDateFormat.date(parsedTask.date, previewCalendar, java.util.Locale.getDefault())) },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                // Time chip
                if (parsedTask.time != null) {
                    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    InputChip(
                        selected = true,
                        onClick = onRemoveTime,
                        label = { Text(parsedTask.time.format(timeFormatter)) },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                // Priority chip
                if (parsedTask.priority != Priority.MEDIUM) {
                    val priorityColor = when (parsedTask.priority) {
                        Priority.HIGH -> MaterialTheme.colorScheme.error
                        Priority.LOW -> MaterialTheme.colorScheme.outline
                        Priority.MEDIUM -> MaterialTheme.colorScheme.primary
                    }
                    val priorityLabel = when (parsedTask.priority) {
                        Priority.HIGH -> stringResource(R.string.priority_high)
                        Priority.LOW -> stringResource(R.string.priority_low)
                        Priority.MEDIUM -> stringResource(R.string.priority_medium)
                    }
                    InputChip(
                        selected = true,
                        onClick = onRemovePriority,
                        label = { Text(priorityLabel, color = priorityColor, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = priorityColor, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                // Recurrence chip
                if (parsedTask.recurrenceRule != RecurrenceRule.None) {
                    val recurrenceLabel = when (parsedTask.recurrenceRule) {
                        RecurrenceRule.Daily -> stringResource(R.string.recurrence_daily)
                        RecurrenceRule.Weekly -> stringResource(R.string.recurrence_weekly)
                        RecurrenceRule.Monthly -> stringResource(R.string.recurrence_monthly)
                        RecurrenceRule.Yearly -> stringResource(R.string.recurrence_yearly)
                        is RecurrenceRule.Interval -> AppDateFormat.localizeDigits(
                            stringResource(
                                R.string.recurrence_interval_abbrev,
                                (parsedTask.recurrenceRule as RecurrenceRule.Interval).days
                            ),
                            Locale.getDefault()
                        )
                        RecurrenceRule.None -> ""
                    }
                    InputChip(
                        selected = true,
                        onClick = onRemoveRecurrence,
                        label = { Text(recurrenceLabel) },
                        leadingIcon = {
                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                // Subtasks checklist chip
                if (parsedTask.subtaskTitles.isNotEmpty()) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                AppDateFormat.localizeDigits(
                                    "${parsedTask.subtaskTitles.size} " + stringResource(R.string.checklist_title),
                                    Locale.getDefault()
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                }

                // Tag chips
                parsedTask.tags.forEach { tagName ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveTag(tagName) },
                        label = { Text("#$tagName") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenEditor,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.smart_open_editor), style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.smart_create_task))
                }
            }
        }
    }
}
