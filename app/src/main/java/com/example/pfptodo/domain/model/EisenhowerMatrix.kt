package com.example.pfptodo.domain.model

/**
 * Domain model representing the 4 quadrants of the Eisenhower Decision Matrix.
 */
data class EisenhowerMatrix(
    val q1DoFirst: List<Task> = emptyList(),      // Urgent & Important (High Priority & Overdue/Due Today/Soon)
    val q2Schedule: List<Task> = emptyList(),     // Not Urgent & Important (High Priority & No Date/Due Later)
    val q3Delegate: List<Task> = emptyList(),     // Urgent & Not Important (Med/Low Priority & Overdue/Due Today/Soon)
    val q4Eliminate: List<Task> = emptyList()     // Not Urgent & Not Important (Med/Low Priority & No Date/Due Later)
) {
    val totalTasks: Int
        get() = q1DoFirst.size + q2Schedule.size + q3Delegate.size + q4Eliminate.size
}
