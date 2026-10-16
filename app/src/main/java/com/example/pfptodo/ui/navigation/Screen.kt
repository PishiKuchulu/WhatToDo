package com.example.pfptodo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GridView
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import android.os.Bundle
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.ui.screens.taskeditor.TaskPrefill
import java.time.Instant

import com.example.pfptodo.R

sealed class Screen(val route: String, val titleRes: Int? = null, val icon: ImageVector? = null) {
    object TaskList : Screen("task_list", R.string.todo_title, Icons.Default.Checklist)
    object Calendar : Screen("calendar", R.string.calendar_title, Icons.Default.CalendarMonth)
    object Birthdays : Screen("birthdays", R.string.birthday_title, Icons.Default.Cake)
    object Statistics : Screen("statistics", R.string.stats_title, Icons.Default.PieChart)
    object Settings : Screen("settings", R.string.settings, Icons.Default.Settings)
    object Matrix : Screen("matrix", R.string.matrix_title, Icons.Default.GridView)

    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: Long) = "task_detail/$taskId"
    }
    object TaskEditor : Screen(
        "task_editor?taskId={taskId}&parentTaskId={parentTaskId}&title={title}&due={due}" +
            "&priority={priority}&recurrence={recurrence}&reminder={reminder}&tags={tags}&subtasks={subtasks}"
    ) {
        private const val LIST_SEPARATOR = "\u001F"

        fun createRoute(
            taskId: Long? = null,
            parentTaskId: Long? = null,
            prefill: TaskPrefill? = null
        ): String {
            val tId = taskId ?: -1L
            val pId = parentTaskId ?: -1L
            val base = "task_editor?taskId=$tId&parentTaskId=$pId"
            if (prefill == null || prefill.isEmpty) return base
            return base +
                "&title=${Uri.encode(prefill.title)}" +
                "&due=${prefill.dueDate?.toEpochMilli() ?: -1L}" +
                "&priority=${prefill.priority.name}" +
                "&recurrence=${Uri.encode(prefill.recurrenceRule.toString())}" +
                "&reminder=${prefill.hasReminder}" +
                "&tags=${Uri.encode(prefill.tagNames.joinToString(LIST_SEPARATOR))}" +
                "&subtasks=${Uri.encode(prefill.subtaskTitles.joinToString(LIST_SEPARATOR))}"
        }

        /** Rebuilds the prefill from the navigation arguments (null when none were passed). */
        fun prefillFrom(args: Bundle?): TaskPrefill? {
            if (args == null) return null
            val title = args.getString("title").orEmpty()
            val due = args.getLong("due", -1L).takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) }
            val priority = args.getString("priority")
                ?.let { name -> Priority.entries.firstOrNull { it.name == name } }
                ?: Priority.MEDIUM
            val recurrence = RecurrenceRule.fromString(args.getString("recurrence"))
            val tags = args.getString("tags").orEmpty().split(LIST_SEPARATOR).filter { it.isNotBlank() }
            val subtasks = args.getString("subtasks").orEmpty().split(LIST_SEPARATOR).filter { it.isNotBlank() }
            val prefill = TaskPrefill(
                title = title,
                dueDate = due,
                priority = priority,
                recurrenceRule = recurrence,
                hasReminder = args.getBoolean("reminder", false),
                tagNames = tags,
                subtaskTitles = subtasks
            )
            return prefill.takeUnless { it.isEmpty }
        }
    }
}
