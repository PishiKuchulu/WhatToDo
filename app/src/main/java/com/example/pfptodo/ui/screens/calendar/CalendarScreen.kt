package com.example.pfptodo.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.DayLoadDensity
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.core.util.CalendarType
import com.example.pfptodo.ui.components.TaskItem
import com.example.pfptodo.ui.theme.LocalCalendarType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onTaskClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val calendarType = LocalCalendarType.current

    // Gregorian navigation lives in the ViewModel. For Jalali the displayed month is tracked here
    // (the ViewModel only knows Gregorian months) and follows the selected day.
    var jalaliMonth by remember { mutableStateOf(AppDateFormat.firstOfMonth(uiState.selectedDate, CalendarType.JALALI)) }
    LaunchedEffect(uiState.selectedDate) {
        jalaliMonth = AppDateFormat.firstOfMonth(uiState.selectedDate, CalendarType.JALALI)
    }
    val displayMonth = if (calendarType == CalendarType.JALALI) jalaliMonth else uiState.currentMonth

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 7-Day Planning Strip
            SevenDayPlanningStrip(
                items = uiState.sevenDayPlan,
                onDateSelected = { viewModel.onDateSelected(it) }
            )

            CalendarHeader(
                currentMonth = displayMonth,
                calendarType = calendarType,
                onMonthChange = { delta ->
                    if (calendarType == CalendarType.JALALI) {
                        jalaliMonth = AppDateFormat.plusMonths(jalaliMonth, delta, CalendarType.JALALI)
                    } else {
                        viewModel.onMonthChange(uiState.currentMonth.plusMonths(delta.toLong()))
                    }
                }
            )

            CalendarGrid(
                currentMonth = displayMonth,
                calendarType = calendarType,
                selectedDate = uiState.selectedDate,
                onDateSelected = { viewModel.onDateSelected(it) },
                tasksForMonth = uiState.tasksForMonth,
                allTasks = uiState.allTasks
            )

            // Routine Streak Intelligence Section
            RoutineStreakSection(
                summary = uiState.streakSummary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = stringResource(R.string.tasks_for_date, AppDateFormat.date(uiState.selectedDate, calendarType, Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.tasksForSelectedDay.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.no_tasks_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.tasksForSelectedDay, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            onToggleCompletion = { viewModel.onToggleTaskCompletion(task) },
                            onDelete = { viewModel.onDeleteTask(task) },
                            modifier = Modifier.clickable { onTaskClick(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarHeader(
    currentMonth: LocalDate,
    calendarType: CalendarType,
    onMonthChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            onMonthChange(-1)
        }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.prev_month))
        }

        Text(
            text = AppDateFormat.monthTitle(currentMonth, calendarType, Locale.getDefault()),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = {
            onMonthChange(1)
        }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next_month))
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: LocalDate,
    calendarType: CalendarType,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    tasksForMonth: List<Task>,
    allTasks: List<Task>
) {
    // Month geometry follows the selected calendar (Jalali months are 29-31 days, weeks start Saturday).
    val firstDayOfMonth = AppDateFormat.firstOfMonth(currentMonth, calendarType)
    val daysInMonth = AppDateFormat.lengthOfMonth(firstDayOfMonth, calendarType)
    val firstDayOfWeek = AppDateFormat.firstDayOfWeek(calendarType, Locale.getDefault())
    val startDayOfWeek = AppDateFormat.leadingBlanks(firstDayOfMonth, firstDayOfWeek)

    val today = remember { LocalDate.now() }
    val densityMap = remember(allTasks, firstDayOfMonth, daysInMonth) {
        val monthEnd = firstDayOfMonth.plusDays(daysInMonth.toLong())
        val dateTasksMap = allTasks.groupBy { task ->
            task.dueDate?.atZone(ZoneId.systemDefault())?.toLocalDate()
        }.filterKeys { it != null && !it.isBefore(firstDayOfMonth) && it.isBefore(monthEnd) }
        val startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val overdueCount = allTasks.count { !it.isCompleted && it.dueDate != null && it.dueDate.isBefore(startOfToday) }

        dateTasksMap.mapValues { (date, tasks) ->
            if (date == null) DayLoadDensity.NONE
            else {
                val scheduledCount = tasks.size
                val highPriority = tasks.count { it.priority == Priority.HIGH && !it.isCompleted }
                val overdue = if (date == today) overdueCount else 0
                val score = scheduledCount + highPriority + overdue
                when {
                    score == 0 -> DayLoadDensity.NONE
                    score in 1..2 -> DayLoadDensity.LOW
                    score in 3..4 -> DayLoadDensity.MEDIUM
                    else -> DayLoadDensity.HIGH
                }
            }
        }
    }

    // Weekday headers ordered from the locale's first day.
    val weekDays = remember(firstDayOfWeek) {
        (0..6).map { offset ->
            val day = firstDayOfWeek.plus(offset.toLong())
            day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
        }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        var currentDay = 1
        for (week in 0..5) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayOfWeek in 0..6) {
                    if (week == 0 && dayOfWeek < startDayOfWeek || currentDay > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val dayDate = firstDayOfMonth.plusDays((currentDay - 1).toLong())
                        val isSelected = dayDate == selectedDate
                        val loadDensity = densityMap[dayDate] ?: DayLoadDensity.NONE

                        CalendarDay(
                            day = currentDay,
                            dayLabel = AppDateFormat.number(currentDay, Locale.getDefault()),
                            date = dayDate,
                            calendarType = calendarType,
                            isSelected = isSelected,
                            loadDensity = loadDensity,
                            onClick = { onDateSelected(dayDate) },
                            modifier = Modifier.weight(1f)
                        )
                        currentDay++
                    }
                }
            }
            if (currentDay > daysInMonth) break
        }
    }
}

@Composable
fun CalendarDay(
    day: Int,
    dayLabel: String,
    date: LocalDate,
    calendarType: CalendarType,
    isSelected: Boolean,
    loadDensity: DayLoadDensity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.cd_calendar_day, AppDateFormat.date(date, calendarType, Locale.getDefault()))
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClickLabel = label) { onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayLabel,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (loadDensity != DayLoadDensity.NONE) {
                val dotColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    when (loadDensity) {
                        DayLoadDensity.LOW -> Color(0xFF4CAF50)
                        DayLoadDensity.MEDIUM -> Color(0xFFFF9800)
                        DayLoadDensity.HIGH -> Color(0xFFE53935)
                        DayLoadDensity.NONE -> Color.Transparent
                    }
                }
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

private fun formatDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
    return date.format(formatter)
}
