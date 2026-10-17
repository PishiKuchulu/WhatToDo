package com.example.pfptodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.core.util.CalendarType
import com.example.pfptodo.core.util.JalaliCalendarConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Solar Hijri date picker. Material's DatePicker is Gregorian-only, so this dialog offers the same
 * pick-a-day flow for the Jalali calendar. It takes and returns a Gregorian [LocalDate].
 */
@Composable
fun JalaliDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val locale = Locale.getDefault()
    val type = CalendarType.JALALI
    var shownMonth by remember { mutableStateOf(AppDateFormat.firstOfMonth(initialDate, type)) }
    var selected by remember { mutableStateOf(initialDate) }

    val year = AppDateFormat.year(shownMonth, type)
    val month = AppDateFormat.month(shownMonth, type)

    fun goToYear(delta: Int) {
        val target = year + delta
        shownMonth = JalaliCalendarConverter.jalaliToGregorian(target, month, 1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = {
            Text(
                text = AppDateFormat.date(selected, type, locale),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Year stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { goToYear(-1) }) { Icon(Icons.Default.Remove, stringResource(R.string.cd_previous_year)) }
                    Text(
                        text = AppDateFormat.number(year, locale),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 64.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { goToYear(1) }) { Icon(Icons.Default.Add, stringResource(R.string.cd_next_year)) }
                }
                // Month stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { shownMonth = AppDateFormat.plusMonths(shownMonth, -1, type) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_previous_month))
                    }
                    Text(
                        text = AppDateFormat.monthName(month, type, locale),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { shownMonth = AppDateFormat.plusMonths(shownMonth, 1, type) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.cd_next_month))
                    }
                }

                val firstDay = AppDateFormat.firstDayOfWeek(type, locale)
                val weekdays = (0..6).map { firstDay.plus(it.toLong()) }
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { dow ->
                        Text(
                            text = dow.getDisplayName(TextStyle.NARROW, locale),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val blanks = AppDateFormat.leadingBlanks(shownMonth, firstDay)
                val length = AppDateFormat.lengthOfMonth(shownMonth, type)
                val cells = blanks + length
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val index = r * 7 + c
                            val day = index - blanks + 1
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (day in 1..length) {
                                    val date = shownMonth.plusDays((day - 1).toLong())
                                    val isSelected = date == selected
                                    val dayDesc = stringResource(
                                        R.string.cd_calendar_day,
                                        AppDateFormat.number(day, locale)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                            .clickable(role = Role.Button) { selected = date }
                                            .semantics {
                                                this.selected = isSelected
                                                contentDescription = dayDesc
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = AppDateFormat.number(day, locale),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
