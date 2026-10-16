package com.example.pfptodo.domain.usecase.calendar

import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.core.util.CalendarType
import com.example.pfptodo.core.util.JalaliCalendarConverter
import com.example.pfptodo.domain.model.DayPlanningItem
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.Task
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Generates a 7-day planning model for the current day and the next 6 days.
 * Fully locale-aware with native Persian (Jalali / Solar Hijri) date formatting.
 */
class GetSevenDayPlanningUseCase(
    private val calculateDailyLoadDensity: CalculateDailyLoadDensityUseCase = CalculateDailyLoadDensityUseCase(),
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val persianMonthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    operator fun invoke(
        allTasks: List<Task>,
        selectedDate: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType? = null
    ): List<DayPlanningItem> {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val useJalali = (calendarType ?: CalendarType.defaultFor(locale)) == CalendarType.JALALI
        val isPersianLocale = locale.language.equals("fa", ignoreCase = true)

        val activeOverdueCount = allTasks.count { task ->
            !task.isCompleted && task.dueDate != null && task.dueDate.isBefore(startOfToday)
        }

        return (0..6).map { dayOffset ->
            val date = today.plusDays(dayOffset.toLong())
            val scheduledTasksForDate = allTasks.filter { task ->
                task.dueDate?.let { it.atZone(zone).toLocalDate() == date } ?: false
            }

            val taskCount = scheduledTasksForDate.size
            val highPriorityCount = scheduledTasksForDate.count { it.priority == Priority.HIGH && !it.isCompleted }
            val overdueCount = if (date == today) activeOverdueCount else 0
            val loadDensity = calculateDailyLoadDensity(date, allTasks)

            val (dayName, dayNum, monthName) = if (useJalali) {
                val jDate = JalaliCalendarConverter.gregorianToJalali(date)
                val dName = if (isPersianLocale) getPersianDayOfWeekName(date.dayOfWeek)
                else date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                Triple(
                    dName,
                    jDate.day.toString(),
                    AppDateFormat.monthName(jDate.month, CalendarType.JALALI, locale, short = true)
                )
            } else {
                val gDayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                val gDayNum = date.dayOfMonth.toString()
                val gMonthName = date.month.getDisplayName(TextStyle.SHORT, locale)
                Triple(gDayName, gDayNum, gMonthName)
            }

            DayPlanningItem(
                date = date,
                dayOfWeekName = dayName,
                displayDayNumber = dayNum,
                displayMonthName = monthName,
                isToday = (date == today),
                isSelected = (date == selectedDate),
                taskCount = taskCount,
                overdueCount = overdueCount,
                highPriorityCount = highPriorityCount,
                loadDensity = loadDensity
            )
        }
    }

    private fun getPersianDayOfWeekName(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.SATURDAY -> "شنبه"
            DayOfWeek.SUNDAY -> "۱شنبه"
            DayOfWeek.MONDAY -> "۲شنبه"
            DayOfWeek.TUESDAY -> "۳شنبه"
            DayOfWeek.WEDNESDAY -> "۴شنبه"
            DayOfWeek.THURSDAY -> "۵شنبه"
            DayOfWeek.FRIDAY -> "جمعه"
        }
    }
}
