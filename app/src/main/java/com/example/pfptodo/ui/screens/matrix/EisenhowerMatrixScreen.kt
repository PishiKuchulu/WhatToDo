package com.example.pfptodo.ui.screens.matrix

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pfptodo.R
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.ui.components.PriorityIndicator
import com.example.pfptodo.ui.components.TagChip
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EisenhowerMatrixScreen(
    viewModel: EisenhowerMatrixViewModel,
    onTaskClick: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val matrixState by viewModel.matrixState.collectAsStateWithLifecycle()
    var selectedQuadrant by remember { mutableStateOf<Int?>(null) } // null = show all 4, 1..4 = expanded quadrant

    // Honor the app's own theme override (WToDoTheme(appTheme)), not the system setting:
    // the active MaterialTheme color scheme already reflects the user's LIGHT/DARK/SYSTEM choice.
    val isAppDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.matrix_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.matrix_subtitle, matrixState.totalTasks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (selectedQuadrant != null) {
                        IconButton(onClick = { selectedQuadrant = null }) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = stringResource(R.string.matrix_show_all)
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AnimatedContent(
                targetState = selectedQuadrant,
                transitionSpec = {
                    fadeIn() + scaleIn(initialScale = 0.95f) togetherWith fadeOut() + scaleOut(targetScale = 0.95f)
                },
                label = "MatrixViewTransition"
            ) { quadrantIndex ->
                if (quadrantIndex == null) {
                    // 2x2 Grid View
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuadrantCard(
                                title = stringResource(R.string.matrix_q1_title),
                                subtitle = stringResource(R.string.matrix_q1_desc),
                                tasks = matrixState.q1DoFirst,
                                icon = Icons.Default.FlashOn,
                                headerColor = Color(0xFFE53935),
                                containerColor = Color(0xFFFFEBEE).takeIf { !isAppDarkTheme } ?: Color(0xFF3E1B1B),
                                isExpanded = false,
                                onExpand = { selectedQuadrant = 1 },
                                onTaskClick = onTaskClick,
                                onToggleCompletion = viewModel::onToggleTaskCompletion,
                                onSnooze = viewModel::onSnoozeTask,
                                modifier = Modifier.weight(1f)
                            )
                            QuadrantCard(
                                title = stringResource(R.string.matrix_q2_title),
                                subtitle = stringResource(R.string.matrix_q2_desc),
                                tasks = matrixState.q2Schedule,
                                icon = Icons.Default.CalendarToday,
                                headerColor = Color(0xFF1E88E5),
                                containerColor = Color(0xFFE3F2FD).takeIf { !isAppDarkTheme } ?: Color(0xFF152A3F),
                                isExpanded = false,
                                onExpand = { selectedQuadrant = 2 },
                                onTaskClick = onTaskClick,
                                onToggleCompletion = viewModel::onToggleTaskCompletion,
                                onSnooze = viewModel::onSnoozeTask,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuadrantCard(
                                title = stringResource(R.string.matrix_q3_title),
                                subtitle = stringResource(R.string.matrix_q3_desc),
                                tasks = matrixState.q3Delegate,
                                icon = Icons.Default.Bolt,
                                headerColor = Color(0xFFFB8C00),
                                containerColor = Color(0xFFFFF3E0).takeIf { !isAppDarkTheme } ?: Color(0xFF3E2D17),
                                isExpanded = false,
                                onExpand = { selectedQuadrant = 3 },
                                onTaskClick = onTaskClick,
                                onToggleCompletion = viewModel::onToggleTaskCompletion,
                                onSnooze = viewModel::onSnoozeTask,
                                modifier = Modifier.weight(1f)
                            )
                            QuadrantCard(
                                title = stringResource(R.string.matrix_q4_title),
                                subtitle = stringResource(R.string.matrix_q4_desc),
                                tasks = matrixState.q4Eliminate,
                                icon = Icons.Default.Inventory2,
                                headerColor = Color(0xFF43A047),
                                containerColor = Color(0xFFE8F5E9).takeIf { !isAppDarkTheme } ?: Color(0xFF193320),
                                isExpanded = false,
                                onExpand = { selectedQuadrant = 4 },
                                onTaskClick = onTaskClick,
                                onToggleCompletion = viewModel::onToggleTaskCompletion,
                                onSnooze = viewModel::onSnoozeTask,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    // Single Expanded Quadrant
                    val (title, subtitle, tasks, icon, headerColor, containerColor) = when (quadrantIndex) {
                        1 -> QuadrantDetails(
                            stringResource(R.string.matrix_q1_title),
                            stringResource(R.string.matrix_q1_desc),
                            matrixState.q1DoFirst,
                            Icons.Default.FlashOn,
                            Color(0xFFE53935),
                            Color(0xFFFFEBEE).takeIf { !isAppDarkTheme } ?: Color(0xFF3E1B1B)
                        )
                        2 -> QuadrantDetails(
                            stringResource(R.string.matrix_q2_title),
                            stringResource(R.string.matrix_q2_desc),
                            matrixState.q2Schedule,
                            Icons.Default.CalendarToday,
                            Color(0xFF1E88E5),
                            Color(0xFFE3F2FD).takeIf { !isAppDarkTheme } ?: Color(0xFF152A3F)
                        )
                        3 -> QuadrantDetails(
                            stringResource(R.string.matrix_q3_title),
                            stringResource(R.string.matrix_q3_desc),
                            matrixState.q3Delegate,
                            Icons.Default.Bolt,
                            Color(0xFFFB8C00),
                            Color(0xFFFFF3E0).takeIf { !isAppDarkTheme } ?: Color(0xFF3E2D17)
                        )
                        else -> QuadrantDetails(
                            stringResource(R.string.matrix_q4_title),
                            stringResource(R.string.matrix_q4_desc),
                            matrixState.q4Eliminate,
                            Icons.Default.Inventory2,
                            Color(0xFF43A047),
                            Color(0xFFE8F5E9).takeIf { !isAppDarkTheme } ?: Color(0xFF193320)
                        )
                    }

                    QuadrantCard(
                        title = title,
                        subtitle = subtitle,
                        tasks = tasks,
                        icon = icon,
                        headerColor = headerColor,
                        containerColor = containerColor,
                        isExpanded = true,
                        onExpand = { selectedQuadrant = null },
                        onTaskClick = onTaskClick,
                        onToggleCompletion = viewModel::onToggleTaskCompletion,
                        onSnooze = viewModel::onSnoozeTask,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private data class QuadrantDetails(
    val title: String,
    val subtitle: String,
    val tasks: List<Task>,
    val icon: ImageVector,
    val headerColor: Color,
    val containerColor: Color
)

@Composable
fun QuadrantCard(
    title: String,
    subtitle: String,
    tasks: List<Task>,
    icon: ImageVector,
    headerColor: Color,
    containerColor: Color,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onTaskClick: (Long) -> Unit,
    onToggleCompletion: (Task) -> Unit,
    onSnooze: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Quadrant Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(headerColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = headerColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Badge(
                    containerColor = headerColor,
                    contentColor = Color.White
                ) {
                    Text(
                        text = AppDateFormat.localizeDigits(tasks.size.toString(), Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onExpand,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.cd_collapse_quadrant else R.string.cd_expand_quadrant
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = headerColor.copy(alpha = 0.2f)
            )

            // Task List
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.matrix_empty_quadrant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        MatrixTaskRow(
                            task = task,
                            onToggleCompletion = { onToggleCompletion(task) },
                            onSnooze = { onSnooze(task) },
                            onClick = { onTaskClick(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MatrixTaskRow(
    task: Task,
    onToggleCompletion: () -> Unit,
    onSnooze: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleCompletion,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = stringResource(R.string.cd_toggle_completion),
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                if (task.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        task.tags.take(2).forEach { tag ->
                            TagChip(tag = tag)
                        }
                    }
                }
            }

            IconButton(
                onClick = onSnooze,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = stringResource(R.string.action_snooze_1d),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
