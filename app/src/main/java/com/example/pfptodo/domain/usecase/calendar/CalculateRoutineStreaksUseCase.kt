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

        // Group tasks into distinct routine series. An anchored series is identified
        // by its anchor together with the normalized title and rule, so two different
        // routines that happen to share an anchor instant never merge. Anchor-less
        // tasks carry no series identity, so each one forms its own series by id and
        // never merges with another anchor-less task.
        val groupedBySeries = recurringTasks.groupBy { task ->
            val anchor = task.recurrenceAnchor
            if (anchor != null) {
                "${anchor}_${task.title.trim().lowercase()}_${task.recurrenceRule}"
            } else {
                "task_${task.id}"
            }
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

            // Consecutive means exactly one period apart on the series' own
            // cadence: a missed period (a multiple of the period) is a break,
            // and there is no lateness tolerance on the pair delta.
            val deltaDays = ChronoUnit.DAYS.between(curr, next)
            val isConsecutive = when (rule) {
                RecurrenceRule.Daily -> deltaDays == 1L
                RecurrenceRule.Weekly -> deltaDays == 7L
                RecurrenceRule.Monthly -> isNextMonthlyOccurrence(curr, next)
                RecurrenceRule.Yearly -> isNextYearlyOccurrence(curr, next)
                is RecurrenceRule.Interval -> deltaDays == rule.days.toLong()
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
            RecurrenceRule.Yearly -> {
                val yearsSince = ChronoUnit.YEARS.between(lastDate.withDayOfYear(1), today.withDayOfYear(1))
                when {
                    yearsSince == 0L -> Pair(runningStreak, true)
                    yearsSince == 1L -> Pair(runningStreak, false)
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

    /**
     * True when [next] is the occurrence exactly one calendar month after
     * [curr] on the series' cadence: the month advances by one AND the day of
     * month stays consistent with the series' anchor day.
     */
    private fun isNextMonthlyOccurrence(curr: LocalDate, next: LocalDate): Boolean {
        val monthDelta = (next.year - curr.year) * 12 + (next.monthValue - curr.monthValue)
        return monthDelta == 1 && isAnchorDayConsistent(curr, next)
    }

    /**
     * True when [next] is the occurrence exactly one calendar year after
     * [curr]: same month, one year later, with the day of month consistent
     * with the series' anchor day.
     */
    private fun isNextYearlyOccurrence(curr: LocalDate, next: LocalDate): Boolean {
        return next.year - curr.year == 1 &&
            next.monthValue == curr.monthValue &&
            isAnchorDayConsistent(curr, next)
    }

    /**
     * Day-of-month consistency between two consecutive occurrences, judged
     * from the dates as given (the previous completion carries the anchor
     * day). Month-end clamping is honoured both ways: an occurrence clamped
     * to the last day of a shorter month (Jan 31 → Feb 28), and the return
     * from a clamped day to the anchor day in the next period (Feb 28 →
     * Mar 31, Feb 29 → Feb 28 the following year).
     */
    private fun isAnchorDayConsistent(curr: LocalDate, next: LocalDate): Boolean {
        if (next.dayOfMonth == curr.dayOfMonth) return true
        val nextClampedDown = next.dayOfMonth == next.lengthOfMonth() && curr.dayOfMonth > next.dayOfMonth
        val currWasClamped = curr.dayOfMonth == curr.lengthOfMonth() && next.dayOfMonth > curr.dayOfMonth
        return nextClampedDown || currWasClamped
    }
}
