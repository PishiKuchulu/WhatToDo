package com.example.pfptodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Tag

val PRESET_TAG_COLORS = listOf(
    "#E57373", // Red
    "#F06292", // Pink
    "#BA68C8", // Purple
    "#9575CD", // Deep Purple
    "#7986CB", // Indigo
    "#64B5F6", // Blue
    "#4DB6AC", // Teal
    "#81C784", // Green
    "#FFD54F", // Amber
    "#FF8A65", // Deep Orange
    "#A1887F", // Brown
    "#90A4AE"  // Blue Grey
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementBottomSheet(
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onCreateTag: (name: String, color: String?) -> Unit,
    onUpdateTag: (tag: Tag) -> Unit,
    onDeleteTag: (tag: Tag) -> Unit
) {
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sell,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.tag_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = { isCreatingNew = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_add_tag),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.add_tag))
                }
            }

            Text(
                text = stringResource(R.string.tag_management_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            HorizontalDivider()

            if (tags.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.no_tags),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tags, key = { it.id }) { tag ->
                        TagManagementRow(
                            tag = tag,
                            onEdit = { editingTag = tag },
                            onDelete = { tagToDelete = tag }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (isCreatingNew) {
        TagEditorDialog(
            title = stringResource(R.string.new_tag),
            initialName = "",
            initialColor = PRESET_TAG_COLORS.first(),
            existingTags = tags,
            onDismiss = { isCreatingNew = false },
            onConfirm = { name, color ->
                onCreateTag(name, color)
                isCreatingNew = false
            }
        )
    }

    editingTag?.let { tag ->
        TagEditorDialog(
            title = stringResource(R.string.edit_tag),
            initialName = tag.name,
            initialColor = tag.color ?: PRESET_TAG_COLORS.first(),
            existingTags = tags.filter { it.id != tag.id },
            onDismiss = { editingTag = null },
            onConfirm = { name, color ->
                onUpdateTag(tag.copy(name = name, color = color))
                editingTag = null
            }
        )
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(text = stringResource(R.string.delete_tag)) },
            text = { Text(text = stringResource(R.string.delete_tag_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tag)
                        tagToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TagManagementRow(
    tag: Tag,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tagColor = parseTagColor(tag.color, MaterialTheme.colorScheme.primary)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(tagColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "#${tag.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.cd_edit_tag, tag.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_tag, tag.name),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TagEditorDialog(
    title: String,
    initialName: String,
    initialColor: String,
    existingTags: List<Tag>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val alreadyExistsText = stringResource(R.string.tag_already_exists)
    val emptyNameText = stringResource(R.string.tag_name_empty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.tag_name)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.tag_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PRESET_TAG_COLORS) { hexColor ->
                        val color = parseTagColor(hexColor)
                        val isSelected = selectedColor.equals(hexColor, ignoreCase = true)
                        val swatchDesc = stringResource(R.string.cd_color_swatch, hexColor)

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.RadioButton) { selectedColor = hexColor }
                                .semantics {
                                    selected = isSelected
                                    contentDescription = swatchDesc
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) {
                        errorMessage = emptyNameText
                        return@Button
                    }
                    val duplicate = existingTags.any { it.name.equals(trimmed, ignoreCase = true) }
                    if (duplicate) {
                        errorMessage = alreadyExistsText
                        return@Button
                    }
                    onConfirm(trimmed, selectedColor)
                },
                enabled = name.isNotBlank()
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
