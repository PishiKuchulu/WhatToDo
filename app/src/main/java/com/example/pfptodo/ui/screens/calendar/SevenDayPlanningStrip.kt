package com.example.pfptodo.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pfptodo.R
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.domain.model.DayLoadDensity
import com.example.pfptodo.domain.model.DayPlanningItem
import java.time.LocalDate
import java.util.Locale

@Composable
fun SevenDayPlanningStrip(
    items: List<DayPlanningItem>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.planning_strip_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items, key = { it.date.toString() }) { item ->
                DayPlanningCard(
                    item = item,
                    onClick = { onDateSelected(item.date) }
                )
            }
        }
    }
}

@Composable
fun DayPlanningCard(
    item: DayPlanningItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = item.isSelected
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderModifier = if (isSelected) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else if (item.isToday) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .width(64.dp)
            .height(86.dp)
            .then(borderModifier)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            // Day Name
            Text(
                text = item.dayOfWeekName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (item.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // Day Number
            Text(
                text = item.displayDayNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Load Density Indicator + Task Count Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (item.taskCount > 0) {
                    val densityColor = when (item.loadDensity) {
                        DayLoadDensity.NONE -> Color.Transparent
                        DayLoadDensity.LOW -> Color(0xFF4CAF50)
                        DayLoadDensity.MEDIUM -> Color(0xFFFF9800)
                        DayLoadDensity.HIGH -> Color(0xFFE53935)
                    }

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(densityColor)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = AppDateFormat.localizeDigits(item.taskCount.toString(), Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Box(modifier = Modifier.size(6.dp))
                }
            }
        }
    }
}
