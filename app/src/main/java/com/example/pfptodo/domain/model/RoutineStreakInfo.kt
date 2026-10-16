package com.example.pfptodo.domain.model

/**
 * Domain model representing streak metrics for a single recurring routine series.
 */
data class RoutineStreakInfo(
    val routineKey: String,
    val title: String,
    val recurrenceRule: RecurrenceRule,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalCompleted: Int,
    val isCompletedForCurrentPeriod: Boolean,
    val consistencyRate: Float
)

/**
 * Summary of all routine streaks across the application.
 */
data class RoutineStreakSummary(
    val routines: List<RoutineStreakInfo> = emptyList(),
    val totalActiveRoutines: Int = 0,
    val overallBestStreak: Int = 0,
    val overallCurrentStreak: Int = 0
)
