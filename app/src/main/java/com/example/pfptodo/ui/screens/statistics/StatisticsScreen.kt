package com.example.pfptodo.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pfptodo.R
import com.example.pfptodo.core.util.AppDateFormat
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val totalTasks = uiState.totalTasks
    val completedTasks = uiState.completedTasks
    val activeTasks = totalTasks - completedTasks
    val overdueTasks = uiState.overdueTasks

    val completionRate = uiState.completionRate

    // Real 7-day completion trend from database data.
    val chartData = entryModelOf(*uiState.completionTrend.map { it.toFloat() }.toTypedArray())

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.overview), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    title = stringResource(R.string.stat_total), 
                    value = AppDateFormat.localizeDigits(totalTasks.toString(), Locale.getDefault()), 
                    icon = Icons.Default.Task, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.stat_done), 
                    value = AppDateFormat.localizeDigits(completedTasks.toString(), Locale.getDefault()), 
                    icon = Icons.Default.CheckCircle, 
                    color = Color(0xFF81C784),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    title = stringResource(R.string.stat_active), 
                    value = AppDateFormat.localizeDigits(activeTasks.toString(), Locale.getDefault()), 
                    icon = Icons.Default.Info, 
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.stat_overdue), 
                    value = AppDateFormat.localizeDigits(overdueTasks.toString(), Locale.getDefault()), 
                    icon = Icons.Default.Error, 
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.completion_rate), style = MaterialTheme.typography.titleMedium)
                    Text(AppDateFormat.localizeDigits(stringResource(R.string.percent_value, completionRate), Locale.getDefault()), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    LinearProgressIndicator(
                        progress = { completionRate / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .padding(top = 8.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            Text(stringResource(R.string.activity_trends), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            val bottomAxisFormatter = remember(uiState.trendDates) {
                com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Horizontal.Bottom> { value, _ ->
                    val index = value.toInt()
                    if (index in uiState.trendDates.indices) {
                        val date = uiState.trendDates[index]
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("E", Locale.getDefault())
                        date.format(formatter)
                    } else {
                        ""
                    }
                }
            }

            Chart(
                chart = lineChart(),
                model = chartData,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            if (uiState.categoryDistribution.isNotEmpty()) {
                Text(stringResource(R.string.category_distribution), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                uiState.categoryDistribution.forEach { (name, count) ->
                    CategoryCountRow(name = name, count = count)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategoryCountRow(name: String, count: Int) {
    val context = LocalContext.current
    val displayName = remember(name) { com.example.pfptodo.core.util.resolveCategoryDisplayName(name, context) }
    Text(
        text = AppDateFormat.localizeDigits(stringResource(R.string.category_tasks_count, displayName, count), Locale.getDefault()),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
