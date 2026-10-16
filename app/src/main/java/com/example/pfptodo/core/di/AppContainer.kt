package com.example.pfptodo.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pfptodo.core.repository.ThemeRepository
import com.example.pfptodo.data.local.PishiDatabase
import com.example.pfptodo.data.local.RoomBackupMaintenance
import com.example.pfptodo.data.local.RoomTransactionRunner
import com.example.pfptodo.data.repository.AttachmentRepositoryImpl
import com.example.pfptodo.data.repository.BirthdayRepositoryImpl
import com.example.pfptodo.data.repository.CategoryRepositoryImpl
import com.example.pfptodo.data.repository.TaskRepositoryImpl
import com.example.pfptodo.domain.repository.AttachmentRepository
import com.example.pfptodo.domain.repository.BirthdayRepository
import com.example.pfptodo.domain.repository.CategoryRepository
import com.example.pfptodo.domain.repository.TaskRepository
import com.example.pfptodo.domain.usecase.backup.BackupUseCases
import com.example.pfptodo.domain.usecase.birthday.*
import com.example.pfptodo.domain.usecase.category.*
import com.example.pfptodo.domain.usecase.task.*
import com.example.pfptodo.ui.screens.birthday.BirthdayViewModel
import com.example.pfptodo.worker.reminder.BirthdayReminderManager
import com.example.pfptodo.worker.reminder.BirthdayReminderManagerImpl
import com.example.pfptodo.worker.reminder.ReminderManager
import com.example.pfptodo.worker.reminder.ReminderManagerImpl
import com.example.pfptodo.ui.screens.calendar.CalendarViewModel
import com.example.pfptodo.ui.screens.tasklist.TaskListViewModel
import com.example.pfptodo.ui.screens.taskdetail.TaskDetailViewModel
import com.example.pfptodo.ui.screens.taskeditor.TaskEditorViewModel
import com.example.pfptodo.ui.screens.statistics.StatisticsViewModel
import com.example.pfptodo.ui.screens.settings.SettingsViewModel

import com.example.pfptodo.data.repository.TagRepositoryImpl
import com.example.pfptodo.domain.repository.TagRepository
import com.example.pfptodo.domain.usecase.tag.*

interface AppContainer {
    val taskRepository: TaskRepository
    val categoryRepository: CategoryRepository
    val attachmentRepository: AttachmentRepository
    val tagRepository: TagRepository
    val themeRepository: ThemeRepository
    val calendarRepository: com.example.pfptodo.core.repository.CalendarRepository
    val languageRepository: com.example.pfptodo.core.repository.LanguageRepository
    val taskUseCases: TaskUseCases
    val categoryUseCases: CategoryUseCases
    val tagUseCases: TagUseCases
    val taskParser: com.example.pfptodo.domain.parser.TaskParser
    val parseSmartTaskUseCase: com.example.pfptodo.domain.usecase.task.ParseSmartTaskUseCase
    val reminderManager: ReminderManager
    val backupUseCases: BackupUseCases
    val birthdayRepository: BirthdayRepository
    val birthdayUseCases: BirthdayUseCases
    val birthdayReminderManager: BirthdayReminderManager

    fun provideTaskListViewModelFactory(): ViewModelProvider.Factory
    fun provideCalendarViewModelFactory(): ViewModelProvider.Factory
    fun provideTaskDetailViewModelFactory(taskId: Long): ViewModelProvider.Factory
    fun provideTaskEditorViewModelFactory(
        taskId: Long?,
        parentTaskId: Long?,
        prefill: com.example.pfptodo.ui.screens.taskeditor.TaskPrefill? = null
    ): ViewModelProvider.Factory
    fun provideStatisticsViewModelFactory(): ViewModelProvider.Factory
    fun provideSettingsViewModelFactory(): ViewModelProvider.Factory
    fun provideEisenhowerMatrixViewModelFactory(): ViewModelProvider.Factory
    fun provideBirthdayViewModelFactory(): ViewModelProvider.Factory
}

class AppContainerImpl(private val context: Context) : AppContainer {

    private val database: PishiDatabase by lazy {
        PishiDatabase.getDatabase(context)
    }

    override val taskRepository: TaskRepository by lazy {
        TaskRepositoryImpl(database.taskDao())
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    override val attachmentRepository: AttachmentRepository by lazy {
        AttachmentRepositoryImpl(database.attachmentDao())
    }

    override val tagRepository: TagRepository by lazy {
        TagRepositoryImpl(database.tagDao())
    }

    override val calendarRepository: com.example.pfptodo.core.repository.CalendarRepository by lazy {
        com.example.pfptodo.core.repository.CalendarRepository(context)
    }

    override val themeRepository: ThemeRepository by lazy {
        ThemeRepository(context)
    }

    override val languageRepository: com.example.pfptodo.core.repository.LanguageRepository by lazy {
        com.example.pfptodo.core.repository.LanguageRepository(context)
    }

    override val taskUseCases: TaskUseCases by lazy {
        TaskUseCases(
            getFilteredTasks = GetFilteredTasksUseCase(taskRepository),
            getTaskById = GetTaskById(taskRepository),
            observeTaskById = ObserveTaskById(taskRepository),
            getSubtasks = GetSubtasks(taskRepository),
            addTask = AddTask(taskRepository),
            updateTask = UpdateTask(taskRepository),
            deleteTask = DeleteTask(taskRepository, reminderManager),
            updateTaskOrder = UpdateTaskOrder(taskRepository),
            swapTaskOrder = SwapTaskOrder(taskRepository),
            toggleTaskCompletion = ToggleTaskCompletionUseCase(taskRepository, reminderManager, tagRepository, RoomTransactionRunner(database)),
            getEisenhowerMatrix = GetEisenhowerMatrixUseCase(taskRepository),
            rescheduleTask = RescheduleTaskUseCase(taskRepository, reminderManager),
            createSubtask = CreateSubtaskUseCase(taskRepository),
            createSubtasksBatch = CreateSubtasksBatchUseCase(taskRepository),
            reorderSubtasks = ReorderSubtasksUseCase(taskRepository),
            getSmartSuggestions = GetSmartSuggestionsUseCase(),
            parseNaturalLanguageSearch = ParseNaturalLanguageSearchUseCase(),
            getSevenDayPlanning = com.example.pfptodo.domain.usecase.calendar.GetSevenDayPlanningUseCase(),
            calculateDailyLoadDensity = com.example.pfptodo.domain.usecase.calendar.CalculateDailyLoadDensityUseCase(),
            calculateRoutineStreaks = com.example.pfptodo.domain.usecase.calendar.CalculateRoutineStreaksUseCase()
        )
    }

    override val categoryUseCases: CategoryUseCases by lazy {
        CategoryUseCases(
            getAllCategories = GetAllCategories(categoryRepository),
            getCategoryById = GetCategoryById(categoryRepository),
            addCategory = AddCategory(categoryRepository),
            updateCategory = UpdateCategory(categoryRepository),
            deleteCategory = DeleteCategory(categoryRepository)
        )
    }

    override val tagUseCases: TagUseCases by lazy {
        TagUseCases(
            getAllTags = GetAllTags(tagRepository),
            getTagById = GetTagById(tagRepository),
            getTagByName = GetTagByName(tagRepository),
            addTag = AddTag(tagRepository),
            updateTag = UpdateTag(tagRepository),
            deleteTag = DeleteTag(tagRepository),
            getTagsForTask = GetTagsForTask(tagRepository),
            setTaskTags = SetTaskTags(tagRepository)
        )
    }

    override val taskParser: com.example.pfptodo.domain.parser.TaskParser by lazy {
        com.example.pfptodo.domain.parser.LocalTaskParser()
    }

    override val parseSmartTaskUseCase: com.example.pfptodo.domain.usecase.task.ParseSmartTaskUseCase by lazy {
        com.example.pfptodo.domain.usecase.task.ParseSmartTaskUseCase(taskParser)
    }

    override val reminderManager: ReminderManager by lazy {
        ReminderManagerImpl(context)
    }

    override val backupUseCases: BackupUseCases by lazy {
        BackupUseCases(
            taskRepository,
            categoryRepository,
            attachmentRepository,
            tagRepository,
            RoomTransactionRunner(database),
            RoomBackupMaintenance(database),
            birthdayRepository
        )
    }

    override val birthdayRepository: BirthdayRepository by lazy {
        BirthdayRepositoryImpl(database.birthdayDao())
    }

    override val birthdayUseCases: BirthdayUseCases by lazy {
        BirthdayUseCases(
            getAllBirthdays = GetAllBirthdays(birthdayRepository),
            getUpcomingBirthdays = GetUpcomingBirthdays(birthdayRepository),
            getBirthdayById = GetBirthdayById(birthdayRepository),
            addBirthday = AddBirthday(birthdayRepository),
            updateBirthday = UpdateBirthday(birthdayRepository),
            deleteBirthday = DeleteBirthday(birthdayRepository)
        )
    }

    override val birthdayReminderManager: BirthdayReminderManager by lazy {
        BirthdayReminderManagerImpl(context)
    }

    /** Re-arms task and birthday reminders, e.g. after a backup restore (WorkManager knows nothing of restored rows). */
    private suspend fun rescheduleAllReminders() {
        taskRepository.getAllTasksRaw()
            .filter { !it.isCompleted && it.reminderTime != null }
            .forEach { reminderManager.scheduleReminder(it) }
        birthdayRepository.getAllBirthdaysRaw().forEach { birthdayReminderManager.scheduleReminder(it) }
    }

    override fun provideTaskListViewModelFactory() = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskListViewModel(taskUseCases, tagUseCases, parseSmartTaskUseCase, themeRepository, reminderManager, categoryUseCases, calendarRepository) as T
        }
    }

    override fun provideCalendarViewModelFactory() = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel(taskUseCases, calendarRepository) as T
        }
    }

    override fun provideTaskDetailViewModelFactory(taskId: Long) = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskDetailViewModel(taskId, taskUseCases, attachmentRepository, reminderManager) as T
        }
    }

    override fun provideTaskEditorViewModelFactory(
        taskId: Long?,
        parentTaskId: Long?,
        prefill: com.example.pfptodo.ui.screens.taskeditor.TaskPrefill?
    ) = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskEditorViewModel(taskId, parentTaskId, taskUseCases, tagUseCases, reminderManager, categoryUseCases, prefill) as T
        }
    }

    override fun provideStatisticsViewModelFactory() = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatisticsViewModel(taskUseCases, categoryUseCases) as T
        }
    }

    override fun provideSettingsViewModelFactory() = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                themeRepository, languageRepository, tagUseCases, backupUseCases, categoryUseCases, calendarRepository,
                onImported = { rescheduleAllReminders() }
            ) as T
        }
    }

    override fun provideEisenhowerMatrixViewModelFactory() = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return com.example.pfptodo.ui.screens.matrix.EisenhowerMatrixViewModel(taskUseCases) as T
        }
    }

    override fun provideBirthdayViewModelFactory() = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BirthdayViewModel(birthdayUseCases, birthdayReminderManager) as T
        }
    }
}
