package com.example.pfptodo.ui.screens.taskdetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Attachment
import com.example.pfptodo.domain.model.AttachmentType
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.ui.components.TagChip
import java.text.SimpleDateFormat
import com.example.pfptodo.core.util.AppDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddSubtask: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val task = uiState.task
    
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val attachments by viewModel.attachments.collectAsStateWithLifecycle(initialValue = emptyList())

    var newSubtaskText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val calendarType = com.example.pfptodo.ui.theme.LocalCalendarType.current

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { pickedUri ->
            // Persist read access so the attachment remains usable after restart.
            try {
                context.contentResolver.takePersistableUriPermission(
                    pickedUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Provider does not support persistence — attachment still usable this session.
            }

            val displayName = queryDisplayName(context, pickedUri)
                ?: context.getString(R.string.attached_file)

            task?.let { t ->
                viewModel.onAddAttachment(
                    Attachment(
                        taskId = t.id,
                        type = attachmentTypeFor(context.contentResolver.getType(pickedUri)),
                        uri = pickedUri.toString(),
                        fileName = displayName
                    )
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_task_confirm_title)) },
            text = { Text(stringResource(R.string.delete_task_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.onDeleteTask()
                    onBack()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { task?.let { onEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { task?.let { onAddSubtask(it.id) } }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_subtask))
            }
        }
    ) { padding ->
        if (task == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.task_not_found))
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriorityChip(priority = task.priority)
                        task.tags.forEach { tag ->
                            TagChip(tag = tag)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!task.description.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.description_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = task.description, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (!task.notes.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.task_notes_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = task.notes, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    DetailItem(icon = Icons.Default.Event, label = stringResource(R.string.due_date_label), value = task.dueDate?.let { AppDateFormat.dateTime(it, java.time.ZoneId.systemDefault(), calendarType, Locale.getDefault()) } ?: stringResource(R.string.no_date_set))

                    if (task.recurrenceRule !is com.example.pfptodo.domain.model.RecurrenceRule.None) {
                        DetailItem(
                            icon = Icons.Default.Repeat,
                            label = stringResource(R.string.recurrence_label),
                            value = when (val rule = task.recurrenceRule) {
                                com.example.pfptodo.domain.model.RecurrenceRule.Daily -> stringResource(R.string.recurrence_daily)
                                com.example.pfptodo.domain.model.RecurrenceRule.Weekly -> stringResource(R.string.recurrence_weekly)
                                com.example.pfptodo.domain.model.RecurrenceRule.Monthly -> stringResource(R.string.recurrence_monthly)
                                com.example.pfptodo.domain.model.RecurrenceRule.Yearly -> stringResource(R.string.recurrence_yearly)
                                is com.example.pfptodo.domain.model.RecurrenceRule.Interval -> stringResource(R.string.recurrence_interval, rule.days)
                                else -> stringResource(R.string.none_label)
                            }
                        )
                    }

                    if (task.reminderTime != null) {
                        DetailItem(
                            icon = Icons.Default.Notifications,
                            label = stringResource(R.string.reminder_label),
                            value = AppDateFormat.dateTime(task.reminderTime, java.time.ZoneId.systemDefault(), calendarType, Locale.getDefault())
                        )
                    }
                }

                HorizontalDivider()
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.attachments_label), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.AttachFile, stringResource(R.string.cd_attach_file))
                    }
                }

                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(attachments) { attachment ->
                        AttachmentItem(
                            attachment = attachment,
                            onOpen = { openAttachment(context, attachment) },
                            onDelete = {
                                releaseAttachmentPermission(context, attachment)
                                viewModel.onDeleteAttachment(attachment)
                            }
                        )
                    }
                }

                HorizontalDivider()

                val completedCount = subtasks.count { it.isCompleted }
                val totalCount = subtasks.size
                val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.checklist_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        if (totalCount > 0) {
                            Text(
                                text = AppDateFormat.localizeDigits(stringResource(R.string.checklist_progress, completedCount, totalCount), Locale.getDefault()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (totalCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = if (completedCount == totalCount) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(
                        items = subtasks,
                        key = { _, it -> it.id }
                    ) { index, subtask ->
                        SubtaskItem(
                            subtask = subtask,
                            onToggle = { viewModel.onToggleSubtask(subtask) },
                            onDelete = { viewModel.onDeleteSubtask(subtask) },
                            canMoveUp = index > 0,
                            canMoveDown = index < subtasks.size - 1,
                            onMoveUp = {
                                val mutable = subtasks.map { it.id }.toMutableList()
                                val temp = mutable[index]
                                mutable[index] = mutable[index - 1]
                                mutable[index - 1] = temp
                                viewModel.onReorderSubtasks(mutable)
                            },
                            onMoveDown = {
                                val mutable = subtasks.map { it.id }.toMutableList()
                                val temp = mutable[index]
                                mutable[index] = mutable[index + 1]
                                mutable[index + 1] = temp
                                viewModel.onReorderSubtasks(mutable)
                            }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newSubtaskText,
                                onValueChange = { newSubtaskText = it },
                                placeholder = { Text(stringResource(R.string.checklist_add_item_hint)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newSubtaskText.isNotBlank()) {
                                        viewModel.onAddSubtask(newSubtaskText)
                                        newSubtaskText = ""
                                    }
                                }),
                                trailingIcon = {
                                    if (newSubtaskText.isNotBlank()) {
                                        IconButton(onClick = {
                                            viewModel.onAddSubtask(newSubtaskText)
                                            newSubtaskText = ""
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
                }

                Button(
                    onClick = { viewModel.onToggleTaskCompletion() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (task.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (task.isCompleted) stringResource(R.string.mark_as_pending) else stringResource(R.string.mark_as_done))
                }
            }
        }
    }
}

@Composable
fun AttachmentItem(attachment: Attachment, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (attachment.type == AttachmentType.IMAGE) {
                AsyncImage(
                    model = android.net.Uri.parse(attachment.uri),
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = attachment.fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_delete), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Maps a MIME type to the stored [AttachmentType]. */
private fun attachmentTypeFor(mime: String?): AttachmentType = when {
    mime == null -> AttachmentType.DOCUMENT
    mime.startsWith("image/") -> AttachmentType.IMAGE
    mime.startsWith("audio/") -> AttachmentType.AUDIO
    mime.startsWith("video/") -> AttachmentType.VIDEO
    else -> AttachmentType.DOCUMENT
}

/** Opens an attachment with whichever installed app can handle it. */
private fun openAttachment(context: android.content.Context, attachment: Attachment) {
    try {
        val uri = android.net.Uri.parse(attachment.uri)
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // No handler, or the file/permission is gone — tell the user instead of crashing.
        android.widget.Toast.makeText(context, R.string.attachment_open_failed, android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Gives back the persisted read grant taken when the file was attached. */
private fun releaseAttachmentPermission(context: android.content.Context, attachment: Attachment) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            android.net.Uri.parse(attachment.uri),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (e: Exception) {
        // Grant was never persisted (or already released) — nothing to do.
    }
}

@Composable
fun SubtaskItem(
    subtask: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = subtask.isCompleted,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = subtask.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = if (subtask.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
        )
        if (canMoveUp) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_move_up),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (canMoveDown) {
            IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_move_down),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_delete),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PriorityChip(priority: Priority) {
    val color = when (priority) {
        Priority.HIGH -> Color(0xFFE57373)
        Priority.MEDIUM -> Color(0xFFFFB74D)
        Priority.LOW -> Color(0xFF81C784)
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = when (priority) {
                Priority.HIGH -> stringResource(R.string.priority_high)
                Priority.MEDIUM -> stringResource(R.string.priority_medium)
                Priority.LOW -> stringResource(R.string.priority_low)
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun formatDate(instant: java.time.Instant): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.getDefault())
    return instant.atZone(java.time.ZoneId.systemDefault()).format(formatter)
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}
