package com.example.pfptodo

import android.app.Application
import com.example.pfptodo.core.di.AppContainer
import com.example.pfptodo.core.di.AppContainerImpl
import com.example.pfptodo.widget.TodoWidgetProvider
import com.example.pfptodo.worker.reminder.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Custom Application class for WToDo.
 * Initializes the [AppContainer] for manual dependency injection and
 * guarantees the notification channel exists before any reminder can fire.
 */
class TodoApplication : Application() {
    lateinit var container: AppContainer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
        NotificationHelper(this).createNotificationChannel(force = false)

        // Keep the home-screen widget in step with the task list (it used to refresh only
        // on the launcher's 30-minute timer, so completed tasks lingered).
        appScope.launch {
            try {
                container.taskRepository.getAllMainTasks()
                    .debounce(500)
                    .collect { TodoWidgetProvider.refresh(this@TodoApplication) }
            } catch (e: Exception) {
                // The widget is cosmetic — never let it take the app down.
            }
        }

        // Self-heal annual birthday reminders (earlier versions scheduled them only once).
        appScope.launch {
            try {
                container.birthdayRepository.getAllBirthdaysRaw().forEach {
                    container.birthdayReminderManager.scheduleReminder(it, replace = false)
                }
            } catch (e: Exception) {
                // Best effort.
            }
        }
    }
}
