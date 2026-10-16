package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.SortOrder
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.model.TaskFilter
import com.example.pfptodo.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Complex use case for retrieving, filtering, and sorting tasks.
 * This logic is encapsulated here to keep ViewModels lean and maintain business rules consistency.
 *
 * Date-boundary semantics (mutually exclusive, anchored to the device's local day):
 *  - TODAY:     dueDate within [startOfToday, startOfTomorrow)
 *  - TOMORROW:  dueDate within [startOfTomorrow, startOfDayAfterTomorrow)
 *  - UPCOMING:  dueDate >= startOfTomorrow (tomorrow and beyond)
 *  - OVERDUE:   active task with dueDate < startOfToday (past days only)
 */
class GetFilteredTasksUseCase(
    private val repository: TaskRepository,
    private val clock: java.time.Clock = java.time.Clock.systemDefaultZone()
) {

    operator fun invoke(
        query: String,
        filter: TaskFilter,
        sortOrder: SortOrder
    ): Flow<List<Task>> {
        val tasksFlow = if (query.isBlank()) {
            repository.getAllMainTasks()
        } else {
            // Normalize the query so Arabic ye/kaf variants match Persian text
            // (the DAO normalizes stored text the same way).
            repository.searchTasks(com.example.pfptodo.core.util.PersianNormalizer.normalize(query))
        }

        return tasksFlow.map { tasks ->
            filterAndSort(tasks, filter, sortOrder)
        }.flowOn(Dispatchers.Default)
    }

    private fun filterAndSort(
        tasks: List<Task>,
        filter: TaskFilter,
        sort: SortOrder
    ): List<Task> {
        val zone = clock.zone ?: ZoneId.systemDefault()
        val localDate = LocalDate.now(clock)
        val startOfToday = localDate.atStartOfDay(zone).toInstant()
        val startOfTomorrow = localDate.plusDays(1).atStartOfDay(zone).toInstant()
        val startOfDayAfterTomorrow = localDate.plusDays(2).atStartOfDay(zone).toInstant()

        // Week boundaries (Saturday-based for Persian / Middle East standard)
        val daysSinceSaturday = ((localDate.dayOfWeek.value % 7) + 1) % 7
        val startOfWeek = localDate.minusDays(daysSinceSaturday.toLong())
        val endOfWeek = startOfWeek.plusDays(7)
        val startOfWeekInstant = startOfWeek.atStartOfDay(zone).toInstant()
        val endOfWeekInstant = endOfWeek.atStartOfDay(zone).toInstant()

        // Also ISO Monday-based week for pure Gregorian queries
        val daysSinceMonday = localDate.dayOfWeek.value - 1
        val startOfIsoWeek = localDate.minusDays(daysSinceMonday.toLong())
        val endOfIsoWeek = startOfIsoWeek.plusDays(7)
        val startOfIsoWeekInstant = startOfIsoWeek.atStartOfDay(zone).toInstant()
        val endOfIsoWeekInstant = endOfIsoWeek.atStartOfDay(zone).toInstant()

        // Month boundaries (Both Jalali Solar Hijri & Gregorian)
        val jalaliDate = com.example.pfptodo.core.util.JalaliCalendarConverter.gregorianToJalali(localDate)
        val startOfJalaliMonth = com.example.pfptodo.core.util.JalaliCalendarConverter.jalaliToGregorian(jalaliDate.year, jalaliDate.month, 1)
        val startOfNextJalaliMonth = if (jalaliDate.month < 12) {
            com.example.pfptodo.core.util.JalaliCalendarConverter.jalaliToGregorian(jalaliDate.year, jalaliDate.month + 1, 1)
        } else {
            com.example.pfptodo.core.util.JalaliCalendarConverter.jalaliToGregorian(jalaliDate.year + 1, 1, 1)
        }
        val startOfJalaliMonthInstant = startOfJalaliMonth.atStartOfDay(zone).toInstant()
        val endOfJalaliMonthInstant = startOfNextJalaliMonth.atStartOfDay(zone).toInstant()

        val startOfGregMonth = localDate.withDayOfMonth(1)
        val endOfGregMonth = startOfGregMonth.plusMonths(1)
        val startOfGregMonthInstant = startOfGregMonth.atStartOfDay(zone).toInstant()
        val endOfGregMonthInstant = endOfGregMonth.atStartOfDay(zone).toInstant()

        return tasks.filter { task ->
            val statusMatch = when (filter.status) {
                TaskFilter.Status.ALL -> true
                TaskFilter.Status.ACTIVE -> !task.isCompleted
                TaskFilter.Status.COMPLETED -> task.isCompleted
                TaskFilter.Status.OVERDUE ->
                    !task.isCompleted && task.dueDate != null && task.dueDate.isBefore(startOfToday)
            }

            val priorityMatch = filter.priorities.isEmpty() || task.priority in filter.priorities
            val categoryMatch = filter.categoryIds.isEmpty() || task.categoryId in filter.categoryIds

            val tagMatch = if (filter.tagIds.isEmpty()) {
                true
            } else {
                val taskTagIds = task.tags.map { it.id }.toSet()
                when (filter.tagFilterMode) {
                    TaskFilter.TagFilterMode.OR -> filter.tagIds.any { it in taskTagIds }
                    TaskFilter.TagFilterMode.AND -> filter.tagIds.all { it in taskTagIds }
                }
            }

            val dateMatch = when (filter.dateFilter) {
                TaskFilter.DateFilter.ALL -> true
                TaskFilter.DateFilter.TODAY ->
                    task.dueDate != null && task.dueDate >= startOfToday && task.dueDate < startOfTomorrow
                TaskFilter.DateFilter.TOMORROW ->
                    task.dueDate != null && task.dueDate >= startOfTomorrow && task.dueDate < startOfDayAfterTomorrow
                TaskFilter.DateFilter.THIS_WEEK ->
                    task.dueDate != null && (
                        (filter.calendarType != com.example.pfptodo.core.util.CalendarType.GREGORIAN &&
                            task.dueDate >= startOfWeekInstant && task.dueDate < endOfWeekInstant) ||
                        (filter.calendarType != com.example.pfptodo.core.util.CalendarType.JALALI &&
                            task.dueDate >= startOfIsoWeekInstant && task.dueDate < endOfIsoWeekInstant)
                    )
                TaskFilter.DateFilter.THIS_MONTH ->
                    task.dueDate != null && (
                        (filter.calendarType != com.example.pfptodo.core.util.CalendarType.GREGORIAN &&
                            task.dueDate >= startOfJalaliMonthInstant && task.dueDate < endOfJalaliMonthInstant) ||
                        (filter.calendarType != com.example.pfptodo.core.util.CalendarType.JALALI &&
                            task.dueDate >= startOfGregMonthInstant && task.dueDate < endOfGregMonthInstant)
                    )
                TaskFilter.DateFilter.UPCOMING ->
                    task.dueDate != null && task.dueDate >= startOfTomorrow
            }

            val recurrenceMatch = !filter.isRecurringOnly || task.recurrenceRule !is com.example.pfptodo.domain.model.RecurrenceRule.None

            statusMatch && priorityMatch && categoryMatch && tagMatch && dateMatch && recurrenceMatch
        }.let { filtered ->
            when (sort) {
                SortOrder.CREATION_DATE -> filtered.sortedByDescending { it.createdAt }
                SortOrder.DUE_DATE -> filtered.sortedWith(
                    compareBy<Task> { it.dueDate == null }.thenBy { it.dueDate }
                )
                SortOrder.PRIORITY -> filtered.sortedByDescending { it.priority.ordinal }
                SortOrder.MANUAL -> filtered.sortedBy { it.orderIndex }
            }
        }
    }
}