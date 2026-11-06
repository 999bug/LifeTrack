package com.example.lifetrack.core.navigation

import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.feature.history.HistoryScreen
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.home.TodayScreen
import com.example.lifetrack.feature.poop.PoopEditorDialog
import com.example.lifetrack.feature.settings.SettingsScreen
import com.example.lifetrack.feature.statistics.StatisticsScreen

private data class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val destinations = listOf(
    Destination("today", "今天", Icons.Filled.Home),
    Destination("history", "记录", Icons.Filled.CalendarMonth),
    Destination("statistics", "统计", Icons.Filled.BarChart),
    Destination("settings", "设置", Icons.Filled.Settings)
)

@Composable
fun LifeTrackApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var editorVisible by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<PoopRecordRelation?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = androidx.compose.ui.Modifier.padding(innerPadding)
        ) {
            composable("today") {
                TodayScreen(
                    viewModel = viewModel,
                    onQuickRecord = viewModel::quickRecord,
                    onDetailedRecord = {
                        editingRecord = null
                        editorVisible = true
                    },
                    onEdit = {
                        editingRecord = it
                        editorVisible = true
                    },
                    onDelete = { pendingDeleteId = it }
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onEdit = {
                        editingRecord = it
                        editorVisible = true
                    },
                    onDelete = { pendingDeleteId = it }
                )
            }
            composable("statistics") { StatisticsScreen(viewModel = viewModel) }
            composable("settings") { SettingsScreen(viewModel = viewModel) }
        }
    }

    if (editorVisible) {
        PoopEditorDialog(
            record = editingRecord,
            onDismiss = { editorVisible = false },
            onSave = { draft ->
                viewModel.saveRecord(editingRecord?.event?.id, draft)
                editorVisible = false
            }
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除这条记录？") },
            text = { Text("删除后无法在应用内撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(id)
                    pendingDeleteId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            }
        )
    }
}
