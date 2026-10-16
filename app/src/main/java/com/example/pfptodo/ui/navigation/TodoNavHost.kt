package com.example.pfptodo.ui.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.pfptodo.TodoApplication
import com.example.pfptodo.ui.screens.calendar.CalendarScreen
import com.example.pfptodo.ui.screens.calendar.CalendarViewModel
import com.example.pfptodo.ui.screens.settings.SettingsScreen
import com.example.pfptodo.ui.screens.settings.SettingsViewModel
import com.example.pfptodo.ui.screens.statistics.StatisticsScreen
import com.example.pfptodo.ui.screens.statistics.StatisticsViewModel
import com.example.pfptodo.ui.screens.taskdetail.TaskDetailScreen
import com.example.pfptodo.ui.screens.taskdetail.TaskDetailViewModel
import com.example.pfptodo.ui.screens.taskeditor.TaskEditorScreen
import com.example.pfptodo.ui.screens.taskeditor.TaskEditorViewModel
import com.example.pfptodo.ui.screens.tasklist.TaskListScreen
import com.example.pfptodo.ui.screens.tasklist.TaskListViewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun TodoNavHost(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as TodoApplication).container

    NavHost(
        navController = navController,
        startDestination = Screen.TaskList.route,
        modifier = modifier,
        enterTransition = { fadeIn() + slideInHorizontally { it } },
        exitTransition = { fadeOut() + slideOutHorizontally { -it } },
        popEnterTransition = { fadeIn() + slideInHorizontally { -it } },
        popExitTransition = { fadeOut() + slideOutHorizontally { it } }
    ) {
        composable(Screen.TaskList.route) {
            val viewModel: TaskListViewModel = viewModel(factory = appContainer.provideTaskListViewModelFactory())
            TaskListScreen(
                viewModel = viewModel,
                onTaskClick = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onAddTask = {
                    navController.navigate(Screen.TaskEditor.createRoute())
                },
                onAddTaskWithPrefill = { prefill ->
                    navController.navigate(Screen.TaskEditor.createRoute(prefill = prefill))
                },
                onOpenMatrix = {
                    navController.navigate(Screen.Matrix.route)
                }
            )
        }

        composable(Screen.Calendar.route) {
            val viewModel: CalendarViewModel = viewModel(factory = appContainer.provideCalendarViewModelFactory())
            CalendarScreen(
                viewModel = viewModel, 
                onTaskClick = { taskId -> navController.navigate(Screen.TaskDetail.createRoute(taskId)) }
            )
        }

        composable(Screen.Statistics.route) {
            val viewModel: StatisticsViewModel = viewModel(factory = appContainer.provideStatisticsViewModelFactory())
            StatisticsScreen(viewModel = viewModel)
        }

        composable(Screen.Birthdays.route) {
            val viewModel: com.example.pfptodo.ui.screens.birthday.BirthdayViewModel = viewModel(
                factory = appContainer.provideBirthdayViewModelFactory()
            )
            com.example.pfptodo.ui.screens.birthday.BirthdayScreen(viewModel = viewModel)
        }

        composable(Screen.Settings.route) {
            // Single activity-scoped instance, hoisted from MainActivity — collecting the
            // same repo flows twice would double-subscribe and duplicate work.
            SettingsScreen(viewModel = settingsViewModel)
        }

        composable(Screen.Matrix.route) {
            val viewModel: com.example.pfptodo.ui.screens.matrix.EisenhowerMatrixViewModel = viewModel(
                factory = appContainer.provideEisenhowerMatrixViewModelFactory()
            )
            com.example.pfptodo.ui.screens.matrix.EisenhowerMatrixScreen(
                viewModel = viewModel,
                onTaskClick = { taskId -> navController.navigate(Screen.TaskDetail.createRoute(taskId)) },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.TaskDetail.route,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "wtodo://task/{taskId}" },
                navDeepLink { uriPattern = "pishitodo://task/{taskId}" }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: -1L
            val viewModel: TaskDetailViewModel = viewModel(
                key = "task_detail_$taskId",
                factory = appContainer.provideTaskDetailViewModelFactory(taskId)
            )
            TaskDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(Screen.TaskEditor.createRoute(taskId = id))
                },
                onAddSubtask = { parentId ->
                    navController.navigate(Screen.TaskEditor.createRoute(parentTaskId = parentId))
                }
            )
        }

        composable(
            route = Screen.TaskEditor.route,
            arguments = listOf(
                navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("parentTaskId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("due") { type = NavType.LongType; defaultValue = -1L },
                navArgument("priority") { type = NavType.StringType; defaultValue = "" },
                navArgument("recurrence") { type = NavType.StringType; defaultValue = "" },
                navArgument("reminder") { type = NavType.BoolType; defaultValue = false },
                navArgument("tags") { type = NavType.StringType; defaultValue = "" },
                navArgument("subtasks") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId").takeIf { it != -1L }
            val parentTaskId = backStackEntry.arguments?.getLong("parentTaskId").takeIf { it != -1L }
            val prefill = Screen.TaskEditor.prefillFrom(backStackEntry.arguments)
            val viewModel: TaskEditorViewModel = viewModel(
                key = "task_editor_${taskId}_${parentTaskId}_${prefill.hashCode()}",
                factory = appContainer.provideTaskEditorViewModelFactory(taskId, parentTaskId, prefill)
            )
            TaskEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
