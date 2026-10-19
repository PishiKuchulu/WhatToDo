package com.example.pfptodo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pfptodo.ui.navigation.AppActions
import com.example.pfptodo.ui.navigation.Screen
import com.example.pfptodo.ui.navigation.TodoNavHost
import com.example.pfptodo.ui.screens.settings.SettingsViewModel
import com.example.pfptodo.ui.theme.WToDoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Main Entry point Activity for the WToDo application.
 * Sets up the Material 3 theme and handles the main navigation structure,
 * including the `wtodo://task/{id}` deep link that opens the task detail.
 */
class MainActivity : AppCompatActivity() {

    /** Deep link pending navigation — updated by onCreate and onNewIntent. */
    private val _pendingDeepLink = MutableStateFlow<Uri?>(null)
    val pendingDeepLink: StateFlow<Uri?> = _pendingDeepLink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as TodoApplication).container

        setContent {
            val navController = rememberNavController()
            val themeViewModel: SettingsViewModel = viewModel(factory = appContainer.provideSettingsViewModelFactory())
            val appTheme by themeViewModel.themeFlow.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Deep-link navigation: cold start (set before setContent) and onNewIntent.
            DeepLinkHandler(navController, pendingDeepLink, onConsumed = { _pendingDeepLink.value = null })

            val navItems = listOf(
                Screen.TaskList,
                Screen.Calendar,
                Screen.Birthdays,
                Screen.Statistics,
                Screen.Settings
            )

            val calendarType by themeViewModel.calendarTypeFlow.collectAsStateWithLifecycle()
            androidx.compose.runtime.CompositionLocalProvider(
                com.example.pfptodo.ui.theme.LocalCalendarType provides calendarType
            ) {
            WToDoTheme(appTheme = appTheme) {
                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        // Only show bottom bar on top-level screens
                        val showBottomBar = navItems.any { it.route == currentDestination?.route }

                        if (showBottomBar) {
                            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                navItems.forEach { screen ->
                                    val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                    NavigationBarItem(
                                        icon = { screen.icon?.let { Icon(it, contentDescription = null) } },
                                        label = { screen.titleRes?.let { Text(stringResource(it), maxLines = 1, softWrap = false) } },
                                        selected = isSelected,
                                        onClick = {
                                            if (!isSelected) {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            }
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    TodoNavHost(
                        navController = navController,
                        settingsViewModel = themeViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
            }
        }

        // Cold-start deep link (e.g., notification tap while app closed). Only on a fresh
        // launch: after a rotation / process restore the old intent must not be replayed.
        if (savedInstanceState == null) {
            _pendingDeepLink.value = intent?.data
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (uri != null && (uri.scheme == "wtodo" || uri.scheme == "pishitodo")) {
            _pendingDeepLink.value = uri
        }
    }
}

@Composable
private fun DeepLinkHandler(
    navController: androidx.navigation.NavHostController,
    deepLink: StateFlow<Uri?>,
    onConsumed: () -> Unit
) {
    val uri by deepLink.collectAsStateWithLifecycle()
    LaunchedEffect(uri) {
        uri?.let { u ->
            if (u.host == "task") {
                extractTaskId(u)?.let { id ->
                    navController.navigate(Screen.TaskDetail.createRoute(id)) {
                        launchSingleTop = true
                    }
                }
            } else if (u.host == "birthday") {
                navController.navigate(Screen.Birthdays.route) {
                    launchSingleTop = true
                }
            } else if (u.host == "action") {
                when (u.lastPathSegment) {
                    "add_task" -> {
                        navController.navigate(Screen.TaskEditor.createRoute()) {
                            launchSingleTop = true
                        }
                    }
                    "matrix" -> {
                        navController.navigate(Screen.Matrix.route) {
                            launchSingleTop = true
                        }
                    }
                    "voice_task" -> {
                        navController.navigate(Screen.TaskList.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                        // The task list opens the voice sheet as soon as it is on screen.
                        AppActions.openVoiceInput.value = true
                    }
                }
            }
            onConsumed()
        }
    }
}

/** Extracts the task id from a `pishitodo://task/{id}` or `pishitodo://task?id=N` URI. */
internal fun extractTaskId(uri: Uri): Long? {
    val pathId = uri.lastPathSegment?.toLongOrNull()
    val queryId = uri.getQueryParameter("id")?.toLongOrNull()
    val id = pathId ?: queryId
    return id?.takeIf { it > 0L }
}