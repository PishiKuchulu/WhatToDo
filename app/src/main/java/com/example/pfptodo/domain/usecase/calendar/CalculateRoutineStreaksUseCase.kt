package com.example.pfptodo.domain.usecase.calendar

import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.RoutineStreakInfo
import com.example.pfptodo.domain.model.RoutineStreakSummary
import com.example.pfptodo.domain.model.Task
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure domain use case that analyzes recurring tasks and their completion history
 * to calculate routine streaks and consistency metrics deterministically without schema changes.
 */
class CalculateRoutineStreaksUseCase(
    private val clock: Clock = Clock.systemDefaultZone()
) {

    operator fun invoke(allTasks: List<Task>): RoutineStreakSummary {
        val zone = clock.zone
        val today = LocalDate.now(clock)

        val recurringTasks = allTasks.filter { it.recurrenceRule !is RecurrenceRule.None }
        if (recurringTasks.isEmpty()) {
            return RoutineStreakSummary()
        }

        // Group tasks into distinct routine series
        val groupedBySeries = recurringTasks.groupBy { task ->
            task.recurrenceAnchor?.toString() ?: "${task.title.trim().lowercase()}_${task.recurrenceRule}"
        }

        val routineList = mutableListOf<RoutineStreakInfo>()

        for ((seriesKey, seriesTasks) in groupedBySeries) {
            val representative = seriesTasks.firstOrNull { !it.isCompleted }
                ?: seriesTasks.maxByOrNull { it.updatedAt }
                ?: seriesTasks.first()

            val title = representative.title.trim()
            val rule = representative.recurrenceRule

            val completedTasks = seriesTasks.filter { it.isCompleted && (it.completedAt != null || it.dueDate != null) }

            if (completedTasks.isEmpty()) {
                routineList.add(
                    RoutineStreakInfo(
                        routineKey = seriesKey,
                        title = title,
                        recurrenceRule = rule,
                        currentStreak = 0,
                        bestStreak = 0,
                        totalCompleted = 0,
                        isCompletedForCurrentPeriod = false,
                        consistencyRate = 0f
                    )
                )
                continue
            }

            // Extract distinct completion dates sorted chronologically
            val distinctDates = completedTasks.mapNotNull { task ->
                (task.completedAt ?: task.dueDate)?.atZone(zone)?.toLocalDate()
            }.distinct().sorted()

            if (distinctDates.isEmpty()) {
                routineList.add(
                    RoutineStreakInfo(
                        routineKey = seriesKey,
                        title = title,
                        recurrenceRule = rule,
                        currentStreak = 0,
                        bestStreak = 0,
                        totalCompleted = completedTasks.size,
                        isCompletedForCurrentPeriod = false,
                        consistencyRate = 0f
                    )
                )
                continue
            }

            val (currentStreak, bestStreak, isCompletedCurrent) = calculateStreakMetrics(
                distinctDates = distinctDates,
                rule = rule,
                today = today
            )

            val totalCompleted = completedTasks.size
            val firstDate = distinctDates.first()
            val daysSpan = maxOf(1L, ChronoUnit.DAYS.between(firstDate, today) + 1)
            val expectedPeriods = when (rule) {
                RecurrenceRule.Daily -> daysSpan
                RecurrenceRule.Weekly -> maxOf(1L, (daysSpan + 6) / 7)
                RecurrenceRule.Monthly -> maxOf(1L, (daysSpan + 29) / 30)
                RecurrenceRule.Yearly -> maxOf(1L, (daysSpan + 364) / 365)
                is RecurrenceRule.Interval -> maxOf(1L, (daysSpan + rule.days - 1) / rule.days)
                RecurrenceRule.None -> 1L
            }

            val consistencyRate = (distinctDates.size.toFloat() / expectedPeriods.toFloat()).coerceIn(0f, 1f)

            routineList.add(
                RoutineStreakInfo(
                    routineKey = seriesKey,
                    title = title,
                    recurrenceRule = rule,
                    currentStreak = currentStreak,
                    bestStreak = bestStreak,
                    totalCompleted = totalCompleted,
                    isCompletedForCurrentPeriod = isCompletedCurrent,
                    consistencyRate = consistencyRate
                )
            )
        }

        val overallBest = routineList.maxOfOrNull { it.bestStreak } ?: 0
        val overallCurrent = routineList.maxOfOrNull { it.currentStreak } ?: 0

        return RoutineStreakSummary(
            routines = routineList,
            totalActiveRoutines = routineList.size,
            overallBestStreak = overallBest,
            overallCurrentStreak = overallCurrent
        )
    }

    private fun calculateStreakMetrics(
        distinctDates: List<LocalDate>,
        rule: RecurrenceRule,
        today: LocalDate
    ): Triple<Int, Int, Boolean> {
        if (distinctDates.isEmpty()) return Triple(0, 0, false)

        var runningStreak = 1
        var maxStreak = 1

        for (i in 0 until distinctDates.size - 1) {
            val curr = distinctDates[i]
            val next = distinctDates[i + 1]

            val isConsecutive = when (rule) {
                RecurrenceRule.Daily -> ChronoUnit.DAYS.between(curr, next) == 1L
                RecurrenceRule.Weekly -> ChronoUnit.DAYS.between(curr, next) in 1L..8L
                RecurrenceRule.Monthly -> ChronoUnit.MONTHS.between(curr.withDayOfMonth(1), next.withDayOfMonth(1)) == 1L
                RecurrenceRule.Yearly -> ChronoUnit.YEARS.between(curr.withDayOfYear(1), next.withDayOfYear(1)) == 1L
                is RecurrenceRule.Interval -> ChronoUnit.DAYS.between(curr, next) in (rule.days - 1L)..(rule.days + 2L)
                RecurrenceRule.None -> false
            }

            if (isConsecutive) {
                runningStreak++
                maxStreak = maxOf(maxStreak, runningStreak)
            } else {
                runningStreak = 1
            }
        }

        val lastDate = distinctDates.last()
        val daysSinceLast = ChronoUnit.DAYS.between(lastDate, today)

        val (currentStreak, isCompletedCurrent) = when (rule) {
            RecurrenceRule.Daily -> {
                when {
                    daysSinceLast == 0L -> Pair(runningStreak, true)
                    daysSinceLast == 1L -> Pair(runningStreak, false)
                    else -> Pair(0, false)
                }
            }
            RecurrenceRule.Weekly -> {
                when {
                    daysSinceLast <= 7L -> Pair(runningStreak, daysSinceLast == 0L)
                    daysSinceLast <= 14L -> Pair(runningStreak, false)
                    else -> Pair(0, false)
                }
            }
            RecurrenceRule.Monthly -> {
                val monthsSince = ChronoUnit.MONTHS.between(lastDate.withDayOfMonth(1), today.withDayOfMonth(1))
                when {
                    monthsSince == 0L -> Pair(runningStreak, true)
                    monthsSince == 1L -> Pair(runningStreak, false)
                    else -> Pair(0, false)
                }
            }
            is RecurrenceRule.Interval -> {
                when {
                    daysSinceLast <= rule.days -> Pair(runningStreak, daysSinceLast == 0L)
                    daysSinceLast <= rule.days * 2 -> Pair(runningStreak, false)
                    else -> Pair(0, false)
                }
            }
            else -> {
                if (daysSinceLast <= 1L) Pair(runningStreak, daysSinceLast == 0L) else Pair(0, false)
            }
        }

        return Triple(currentStreak, maxStreak, isCompletedCurrent)
    }
}
