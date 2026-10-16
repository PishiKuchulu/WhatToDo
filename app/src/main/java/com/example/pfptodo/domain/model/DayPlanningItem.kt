package com.example.pfptodo.domain.model

import java.time.LocalDate

/**
 * Represents a single day item in the 7-day planning strip.
 */
data class DayPlanningItem(
    val date: LocalDate,
    val dayOfWeekName: String,
    val displayDayNumber: String,
    val displayMonthName: String,
    val isToday: Boolean,
    val isSelected: Boolean,
    val taskCount: Int,
    val overdueCount: Int,
    val highPriorityCount: Int,
    val loadDensity: DayLoadDensity
)
