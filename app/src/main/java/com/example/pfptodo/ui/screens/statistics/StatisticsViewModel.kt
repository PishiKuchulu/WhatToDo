package com.example.pfptodo.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfptodo.domain.model.Category
import com.example.pfptodo.domain.model.Task
import com.example.pfptodo.domain.usecase.category.CategoryUseCases
import com.example.pfptodo.domain.usecase.task.TaskUseCases
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * ViewModel for the Statistics screen.
 * Computes real metrics from task data: totals, completion rate, overdue,
 * a 7-day completion trend and per-category distribution.
 */
class StatisticsViewModel(
    private val taskUseCases: TaskUseCases,
    private val categoryUseCases: CategoryUseCases
) : ViewModel() {

    private val categories: StateFlow<List<Category>> = categoryUseCases.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<StatisticsUiState> = combine(
        taskUseCases.getFilteredTasks("", com.example.pfptodo.domain.model.TaskFilter(), com.example.pfptodo.domain.model.SortOrder.CREATION_DATE),
        categories
    ) { tasks, cats ->
        computeStatistics(tasks, cats)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState()
    )

    private fun computeStatistics(tasks: List<Task>, categories: List<Category>): StatisticsUiState {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val overdue = tasks.count { !it.isCompleted && it.dueDate?.isBefore(startOfToday) == true }

        // 7-day completion trend (real data).
        val today = LocalDate.now(zone)
        val perDay = (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            val count = tasks.count { task ->
                task.completedAt != null && task.completedAt.atZone(zone).toLocalDate() == day
            }
            day to count
        }

        // Category distribution (main tasks only, by category name).
        val namesById = categories.associate { it.id to it.name }
        val categoryDistribution = tasks
            .filter { it.categoryId != null }
            .groupBy { namesById[it.categoryId] ?: it.categoryId.toString() }
            .mapValues { (_, list) -> list.size }
            .toSortedMap()

        return StatisticsUiState(
            totalTasks = total,
            completedTasks = completed,
            overdueTasks = overdue,
            completionRate = if (total > 0) (completed.toFloat() / total * 100).toInt() else 0,
            completionTrend = perDay.map { it.second },
            trendDates = perDay.map { it.first },
            categoryDistribution = categoryDistribution
        )
    }
}

data class StatisticsUiState(
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val overdueTasks: Int = 0,
    val completionRate: Int = 0,
    val completionTrend: List<Int> = List(7) { 0 },
    val trendDates: List<LocalDate> = emptyList(),
    val categoryDistribution: Map<String, Int> = emptyMap()
)