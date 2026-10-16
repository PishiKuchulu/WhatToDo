package com.example.pfptodo.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pfptodo.R
import com.example.pfptodo.core.util.AppDateFormat
import com.example.pfptodo.core.util.CalendarType
import com.example.pfptodo.ui.components.CategoryManagementSheet
import com.example.pfptodo.ui.components.TagManagementBottomSheet
import com.example.pfptodo.ui.theme.AppTheme

/**
 * Modern, beautifully categorized Settings screen for WToDo.
 * Organized into 4 distinct groups:
 * 1. Personalization (Theme & Language)
 * 2. Organization (Tag Management)
 * 3. Data & Storage (Backup & Restore)
 * 4. About WToDo (Version, Developer, License)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val currentTheme by viewModel.themeFlow.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.languageFlow.collectAsStateWithLifecycle()
    val tags by viewModel.tagsFlow.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val categories by viewModel.categoriesFlow.collectAsStateWithLifecycle()
    val currentCalendar by viewModel.calendarTypeFlow.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTagManagementSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }

    val supportedLanguages = remember {
        listOf(
            "system" to R.string.language_system,
            "en" to R.string.language_en,
            "fa" to R.string.language_fa,
            "es" to R.string.language_es,
            "fr" to R.string.language_fr,
            "de" to R.string.language_de,
            "it" to R.string.language_it,
            "pt" to R.string.language_pt,
            "ru" to R.string.language_ru,
            "ar" to R.string.language_ar,
            "tr" to R.string.language_tr,
            "zh-CN" to R.string.language_zh_cn,
            "ja" to R.string.language_ja
        )
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportData(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(context, it) }
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme_settings), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppTheme.entries.forEach { theme ->
                        val themeName = when (theme) {
                            AppTheme.LIGHT -> stringResource(R.string.theme_light)
                            AppTheme.DARK -> stringResource(R.string.theme_dark)
                            AppTheme.SYSTEM -> stringResource(R.string.theme_follow_system)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onThemeSelected(theme)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = themeName, style = MaterialTheme.typography.bodyLarge)
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = {
                                    viewModel.onThemeSelected(theme)
                                    showThemeDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Calendar Selection Dialog (Gregorian / Jalali). Only the display changes; stored dates do not.
    if (showCalendarDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarDialog = false },
            title = { Text(stringResource(R.string.calendar_type_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.calendar_type_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CalendarType.entries.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onCalendarTypeSelected(type)
                                    showCalendarDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(if (type == CalendarType.JALALI) R.string.calendar_jalali else R.string.calendar_gregorian),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            RadioButton(
                                selected = currentCalendar == type,
                                onClick = {
                                    viewModel.onCalendarTypeSelected(type)
                                    showCalendarDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_settings), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    supportedLanguages.forEach { (tag, nameRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onLanguageSelected(tag)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(nameRes), style = MaterialTheme.typography.bodyLarge)
                            RadioButton(
                                selected = currentLanguage == tag,
                                onClick = {
                                    viewModel.onLanguageSelected(tag)
                                    showLanguageDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCategorySheet) {
        CategoryManagementSheet(
            categories = categories,
            onDismiss = { showCategorySheet = false },
            onCreate = viewModel::onCreateCategory,
            onUpdate = viewModel::onUpdateCategory,
            onDelete = viewModel::onDeleteCategory
        )
    }

    if (showTagManagementSheet) {
        TagManagementBottomSheet(
            tags = tags,
            onDismiss = { showTagManagementSheet = false },
            onCreateTag = viewModel::onCreateTag,
            onUpdateTag = viewModel::onUpdateTag,
            onDeleteTag = viewModel::onDeleteTag
        )
    }

    LaunchedEffect(exportStatus) {
        exportStatus?.let {
            when (it) {
                TransferStatus.SUCCESS -> snackbarHostState.showSnackbar(context.getString(R.string.success_export))
                TransferStatus.CANCELLED -> snackbarHostState.showSnackbar(context.getString(R.string.cancelled))
                else -> snackbarHostState.showSnackbar(context.getString(R.string.error_export))
            }
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(importStatus) {
        importStatus?.let {
            when (it) {
                TransferStatus.SUCCESS -> snackbarHostState.showSnackbar(context.getString(R.string.success_import))
                TransferStatus.INVALID -> snackbarHostState.showSnackbar(context.getString(R.string.invalid_backup))
                TransferStatus.CANCELLED -> snackbarHostState.showSnackbar(context.getString(R.string.cancelled))
                else -> snackbarHostState.showSnackbar(context.getString(R.string.error_import))
            }
            viewModel.clearStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Category 1: Personalization (Appearance & Language)
            CategoryCard(
                categoryTitle = stringResource(R.string.appearance),
                categoryIcon = Icons.Default.Palette
            ) {
                val currentThemeName = when (currentTheme) {
                    AppTheme.LIGHT -> stringResource(R.string.theme_light)
                    AppTheme.DARK -> stringResource(R.string.theme_dark)
                    AppTheme.SYSTEM -> stringResource(R.string.theme_follow_system)
                }
                SettingsRow(
                    icon = Icons.Default.BrightnessMedium,
                    title = stringResource(R.string.theme_settings),
                    subtitle = currentThemeName,
                    onClick = { showThemeDialog = true }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                val currentLanguageName = supportedLanguages.find { it.first == currentLanguage }?.second?.let {
                    stringResource(it)
                } ?: stringResource(R.string.language_system)

                SettingsRow(
                    icon = Icons.Default.Translate,
                    title = stringResource(R.string.language),
                    subtitle = currentLanguageName,
                    onClick = { showLanguageDialog = true }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    icon = Icons.Default.CalendarMonth,
                    title = stringResource(R.string.calendar_type_title),
                    subtitle = stringResource(
                        if (currentCalendar == CalendarType.JALALI) R.string.calendar_jalali else R.string.calendar_gregorian
                    ),
                    onClick = { showCalendarDialog = true }
                )
            }

            // Category 2: Organization (Tags & Categories)
            CategoryCard(
                categoryTitle = stringResource(R.string.settings_organization),
                categoryIcon = Icons.Default.Sell
            ) {
                SettingsRow(
                    icon = Icons.Default.Category,
                    title = stringResource(R.string.category_management),
                    subtitle = stringResource(R.string.category_management_desc),
                    badge = "${categories.size}",
                    onClick = { showCategorySheet = true }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Label,
                    title = stringResource(R.string.tag_management),
                    subtitle = stringResource(R.string.tag_management_desc),
                    badge = AppDateFormat.localizeDigits(tags.size.toString(), java.util.Locale.getDefault()),
                    onClick = { showTagManagementSheet = true }
                )
            }

            // Notifications: status + shortcut to the system notification settings.
            NotificationsCard(context = context)

            // Category 3: Data & Storage (Backup & Restore)
            CategoryCard(
                categoryTitle = stringResource(R.string.data_label),
                categoryIcon = Icons.Default.Storage
            ) {
                SettingsRow(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.export_tasks),
                    subtitle = stringResource(R.string.export_tasks_desc),
                    enabled = !isBusy,
                    onClick = { exportLauncher.launch("wtodo_backup.json") }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.import_tasks),
                    subtitle = stringResource(R.string.import_tasks_desc),
                    enabled = !isBusy,
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )
            }

            // Category 4: About WToDo
            CategoryCard(
                categoryTitle = stringResource(R.string.about_label),
                categoryIcon = Icons.Default.Info
            ) {
                SettingsRow(
                    icon = Icons.Default.Checklist,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.version_info)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.developer_name),
                    subtitle = stringResource(R.string.developer_label)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsRow(
                    icon = Icons.Default.Verified,
                    title = stringResource(R.string.license_name),
                    subtitle = stringResource(R.string.license_label)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Reminder notification status (re-read on resume) and a shortcut to the system settings. */
@Composable
private fun NotificationsCard(context: android.content.Context) {
    var enabled by remember {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                enabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CategoryCard(
        categoryTitle = stringResource(R.string.settings_notifications),
        categoryIcon = Icons.Default.Notifications
    ) {
        SettingsRow(
            icon = if (enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
            title = stringResource(
                if (enabled) R.string.notifications_enabled_state else R.string.notifications_disabled_state
            )
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        SettingsRow(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.notification_system_settings),
            onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                try { context.startActivity(intent) } catch (e: Exception) { /* no settings handler */ }
            }
        )
    }
}

/**
 * Grouped category card container with header icon and clean title.
 */
@Composable
private fun CategoryCard(
    categoryTitle: String,
    categoryIcon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = categoryTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

/**
 * Individual setting row with icon, title, subtitle, badge/trailing control, and optional click.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (badge != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
