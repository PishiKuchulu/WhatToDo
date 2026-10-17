package com.example.pfptodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.core.util.getDisplayName
import com.example.pfptodo.domain.model.Category

/** Icons a category can use. Keys are what is stored in `Category.iconName`. */
val CATEGORY_ICONS: Map<String, ImageVector> = linkedMapOf(
    "person" to Icons.Default.Person,
    "work" to Icons.Default.Work,
    "shopping_cart" to Icons.Default.ShoppingCart,
    "school" to Icons.Default.School,
    "home" to Icons.Default.Home,
    "favorite" to Icons.Default.Favorite,
    "star" to Icons.Default.Star,
    "fitness_center" to Icons.Default.FitnessCenter,
    "flight" to Icons.Default.Flight,
    "restaurant" to Icons.Default.Restaurant,
    "savings" to Icons.Default.Savings,
    "pets" to Icons.Default.Pets
)

fun categoryIcon(iconName: String): ImageVector = CATEGORY_ICONS[iconName] ?: Icons.Default.Category

fun parseCategoryColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCreate: (name: String, color: String, iconName: String) -> Unit,
    onUpdate: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Category?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.category_management),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.category_add))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.category_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(categories, key = { it.id }) { category ->
                        val color = parseCategoryColor(category.color, MaterialTheme.colorScheme.primary)
                        ListItem(
                            headlineContent = { Text(category.getDisplayName(context)) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(categoryIcon(category.iconName), null, tint = color, modifier = Modifier.size(20.dp))
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editing = category }) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                    }
                                    IconButton(onClick = { toDelete = category }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.cd_delete))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        CategoryEditorDialog(
            title = stringResource(R.string.category_add),
            initial = null,
            existing = categories,
            onDismiss = { creating = false },
            onConfirm = { name, color, icon ->
                onCreate(name, color, icon)
                creating = false
            }
        )
    }
    editing?.let { category ->
        CategoryEditorDialog(
            title = stringResource(R.string.category_edit),
            initial = category,
            existing = categories,
            onDismiss = { editing = null },
            onConfirm = { name, color, icon ->
                onUpdate(category.copy(name = name, color = color, iconName = icon))
                editing = null
            }
        )
    }
    toDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_message, category.getDisplayName(context))) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(category)
                    toDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    title: String,
    initial: Category?,
    existing: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: String, iconName: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.getDisplayName(context) ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: PRESET_TAG_COLORS.first()) }
    var icon by remember { mutableStateOf(initial?.iconName ?: CATEGORY_ICONS.keys.first()) }

    val trimmed = name.trim()
    val duplicate = existing.any {
        it.id != initial?.id && it.getDisplayName(context).equals(trimmed, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name_label)) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text(stringResource(R.string.category_name_duplicate)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_TAG_COLORS.forEach { hex ->
                        val c = parseCategoryColor(hex, Color.Gray)
                        val isSelectedColor = hex == color
                        val swatchDesc = stringResource(R.string.cd_color_swatch, hex)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.RadioButton) { color = hex }
                                .semantics {
                                    selected = isSelectedColor
                                    contentDescription = swatchDesc
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(c, CircleShape)
                                    .border(
                                        width = if (isSelectedColor) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_ICONS.forEach { (key, vector) ->
                        FilterChip(
                            selected = key == icon,
                            onClick = { icon = key },
                            label = { Icon(vector, null, modifier = Modifier.size(20.dp)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed, color, icon) }, enabled = trimmed.isNotEmpty() && !duplicate) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
