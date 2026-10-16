package com.example.pfptodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.pfptodo.MainActivity
import com.example.pfptodo.R
import com.example.pfptodo.TodoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Home-screen widget showing the nearest active tasks, with a one-tap add action.
 *
 * Reads through the same repository the app uses, so the widget and the app can
 * never disagree about what is on the list.
 */
class TodoWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val titles = loadUpcomingTitles(appContext)
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, titles))
                }
            } catch (e: Exception) {
                // A widget must never crash the host launcher — fall back to empty.
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, emptyList()))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Up to [MAX_TASKS] active task titles, soonest due first, undated last. */
    private suspend fun loadUpcomingTitles(context: Context): List<String> {
        val app = context.applicationContext as? TodoApplication ?: return emptyList()
        val tasks = app.container.taskRepository.getAllMainTasks().first()
        val zone = ZoneId.systemDefault()
        val startOfToday = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone).toInstant()

        return tasks
            .filter { !it.isCompleted && it.parentTaskId == null }
            .sortedWith(
                compareBy(
                    { it.dueDate == null },
                    { it.dueDate ?: startOfToday },
                    { -it.priority.ordinal }
                )
            )
            .take(MAX_TASKS)
            .map { it.title }
    }

    private fun buildViews(context: Context, titles: List<String>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_todo)
        val container = R.id.widget_task_container

        views.removeAllViews(container)

        if (titles.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            for (title in titles) {
                val row = RemoteViews(context.packageName, R.layout.widget_task_row)
                row.setTextViewText(R.id.widget_task_title, title)
                views.addView(container, row)
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_task_container,
            pending(context, openIntent, 1)
        )

        val addIntent = Intent(Intent.ACTION_VIEW, Uri.parse("wtodo://action/add_task"), context, MainActivity::class.java)
        views.setOnClickPendingIntent(
            R.id.widget_add_button,
            pending(context, addIntent, 2)
        )

        return views
    }

    private fun pending(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val MAX_TASKS = 5

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(context.applicationContext, TodoWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val intent = Intent(context.applicationContext, TodoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.applicationContext.sendBroadcast(intent)
        }
    }
}
