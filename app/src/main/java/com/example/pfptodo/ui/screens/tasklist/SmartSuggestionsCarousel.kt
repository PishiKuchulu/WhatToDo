package com.example.pfptodo.ui.screens.tasklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.SuggestionAction
import com.example.pfptodo.domain.model.SuggestionType
import com.example.pfptodo.domain.model.TaskSuggestion

@Composable
fun SmartSuggestionsCarousel(
    suggestions: List<TaskSuggestion>,
    onDismiss: (String) -> Unit,
    onAction: (SuggestionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = suggestions,
                key = { it.id }
            ) { suggestion ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(200)) + shrinkHorizontally(tween(200))
                ) {
                    SuggestionCard(
                        suggestion = suggestion,
                        onDismiss = { onDismiss(suggestion.id) },
                        onAction = { onAction(suggestion.action) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: TaskSuggestion,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (titleRes, descRes, icon) = when (suggestion.type) {
        SuggestionType.OVERDUE_HIGH_PRIORITY -> Triple(
            R.string.suggestion_overdue_high_title,
            R.string.suggestion_overdue_high_desc,
            Icons.Default.WarningAmber
        )
        SuggestionType.OVERDUE_CLEANUP -> Triple(
            R.string.suggestion_overdue_cleanup_title,
            R.string.suggestion_overdue_cleanup_desc,
            Icons.Default.WarningAmber
        )
        SuggestionType.HIGH_PRIORITY_UNSCHEDULED -> Triple(
            R.string.suggestion_unscheduled_high_title,
            R.string.suggestion_unscheduled_high_desc,
            Icons.Default.Schedule
        )
        SuggestionType.STALE_TASK -> Triple(
            R.string.suggestion_stale_title,
            R.string.suggestion_stale_desc,
            Icons.AutoMirrored.Filled.EventNote
        )
        SuggestionType.RECURRING_TASK_REVIEW -> Triple(
            R.string.suggestion_recurring_review_title,
            R.string.suggestion_recurring_review_desc,
            Icons.Default.AutoAwesome
        )
        SuggestionType.CHECKLIST_PROGRESS -> Triple(
            R.string.suggestion_checklist_title,
            R.string.suggestion_checklist_desc,
            Icons.Default.AutoAwesome
        )
        SuggestionType.DAILY_TRIAGE -> Triple(
            R.string.suggestion_daily_triage_title,
            R.string.suggestion_daily_triage_desc,
            Icons.Default.AutoAwesome
        )
    }

    val formattedDesc = formatSuggestionDescription(descRes, suggestion.formatArgs)

    val actionLabelRes = when (suggestion.action) {
        is SuggestionAction.OpenTask -> R.string.suggestion_action_view
        is SuggestionAction.OpenTaskEditor -> R.string.suggestion_action_schedule
        is SuggestionAction.FilterOverdue,
        is SuggestionAction.FilterToday -> R.string.suggestion_action_triage
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .widthIn(min = 260.dp, max = 320.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_dismiss_suggestion),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formattedDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = stringResource(actionLabelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun formatSuggestionDescription(stringResId: Int, args: List<String>): String {
    return when (args.size) {
        0 -> stringResource(stringResId)
        1 -> stringResource(stringResId, args[0])
        2 -> stringResource(stringResId, args[0], args[1])
        3 -> stringResource(stringResId, args[0], args[1], args[2])
        else -> stringResource(stringResId, *args.toTypedArray())
    }
}
