package com.example.pfptodo.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.domain.model.DayPlanningItem
import com.example.pfptodo.domain.model.RoutineStreakSummary
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * ViewModel for the Calendar screen.
 * Manages the state for date selection, month navigation, 7-day planning strip,
 * workload density, routine streaks, and task retrieval for the calendar view.
 */
class CalendarViewModel(
    private val taskUseCases: TaskUseCases,
    calendarRepository: com.example.pfptodo.core.repository.CalendarRepository? = null
) : ViewModel() {

    private val calendarTypeFlow: Flow<com.example.pfptodo.core.util.CalendarType?> =
        calendarRepository?.calendarTypeFlow ?: flowOf(null)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val currentMonth = _currentMonth.asStateFlow()

    val tasks: StateFlow<List<Task>> = taskUseCases.getFilteredTasks(
        "",
        com.example.pfptodo.domain.model.TaskFilter(status = com.example.pfptodo.domain.model.TaskFilter.Status.ALL),
        com.example.pfptodo.domain.model.SortOrder.DUE_DATE
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val sevenDayPlanFlow: Flow<List<DayPlanningItem>> = combine(
        tasks,
        _selectedDate,
        calendarTypeFlow
    ) { allTasks, selectedDate, calendarType ->
        taskUseCases.getSevenDayPlanning(allTasks, selectedDate, Locale.getDefault(), calendarType)
    }.distinctUntilChanged()

    private val streakSummaryFlow: Flow<RoutineStreakSummary> = tasks.map { allTasks ->
        taskUseCases.calculateRoutineStreaks(allTasks)
    }.distinctUntilChanged()

    val uiState: StateFlow<CalendarUiState> = combine(
        _selectedDate,
        _currentMonth,
        tasks,
        sevenDayPlanFlow,
        streakSummaryFlow
    ) { selectedDate, currentMonth, allTasks, sevenDayPlan, streakSummary ->
        val zone = ZoneId.systemDefault()
        val tasksForSelectedDay = allTasks.filter { task ->
            task.dueDate?.let { it.atZone(zone).toLocalDate() == selectedDate } ?: false
        }
        val tasksForMonth = allTasks.filter { task ->
            task.dueDate?.let {
                val d = it.atZone(zone).toLocalDate()
                d.year == currentMonth.year && d.month == currentMonth.month
            } ?: false
        }

        CalendarUiState(
            selectedDate = selectedDate,
            currentMonth = currentMonth,
            sevenDayPlan = sevenDayPlan,
            streakSummary = streakSummary,
            allTasks = allTasks,
            tasksForSelectedDay = tasksForSelectedDay,
            tasksForMonth = tasksForMonth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        if (date.year != _currentMonth.value.year || date.month != _currentMonth.value.month) {
            _currentMonth.value = date.withDayOfMonth(1)
        }
    }

    fun onMonthChange(newMonth: LocalDate) {
        _currentMonth.value = newMonth.withDayOfMonth(1)
    }

    fun onToggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskUseCases.toggleTaskCompletion(task)
        }
    }

    fun onDeleteTask(task: Task) {
        viewModelScope.launch {
            taskUseCases.deleteTask(task)
        }
    }
}

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val currentMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val sevenDayPlan: List<DayPlanningItem> = emptyList(),
    val streakSummary: RoutineStreakSummary = RoutineStreakSummary(),
    val allTasks: List<Task> = emptyList(),
    val tasksForSelectedDay: List<Task> = emptyList(),
    val tasksForMonth: List<Task> = emptyList()
)
