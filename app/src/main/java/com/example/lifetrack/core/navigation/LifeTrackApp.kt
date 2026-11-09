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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.model.toEntity
import com.example.lifetrack.feature.coffee.CoffeeEditorDialog
import com.example.lifetrack.feature.history.HistoryScreen
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.home.TodayScreen
import com.example.lifetrack.feature.poop.PoopEditorDialog
import com.example.lifetrack.feature.settings.ModuleManagementScreen
import com.example.lifetrack.feature.settings.SettingsScreen
import com.example.lifetrack.feature.sport.SportEditorDialog
import com.example.lifetrack.feature.statistics.StatisticsScreen
import com.example.lifetrack.feature.water.WaterEditorDialog

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

private fun BackupRecord.toPoopRelation() = PoopRecordRelation(
    event = event,
    detail = (detail as? RecordDetail.Poop)?.toEntity(event.id)
)

@Composable
fun LifeTrackApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var editorVisible by remember { mutableStateOf(false) }
    var editorTypeCode by remember { mutableStateOf(DEFAULT_POOP_TYPE.code) }
    var editingRecord by remember { mutableStateOf<BackupRecord?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdate(auto = true)
    }

    fun openEditor(typeCode: String, record: BackupRecord?) {
        editorTypeCode = typeCode
        editingRecord = record
        editorVisible = true
    }

    Scaffold(
        bottomBar = {
            // 记录项目是设置下的子页面，隐藏底部导航，避免「看似平级」的误解
            if (destinations.any { it.route == currentRoute }) {
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
                    onDetailedRecord = { openEditor(DEFAULT_POOP_TYPE.code, null) },
                    onWaterRecord = { openEditor(WaterFields.TYPE_CODE, null) },
                    onCoffeeRecord = { openEditor(CoffeeFields.TYPE_CODE, null) },
                    onSportRecord = { openEditor(SportFields.TYPE_CODE, null) },
                    onOpenSettings = { navController.navigate("settings") },
                    onEdit = { openEditor(it.event.typeCode, it) },
                    onDelete = { pendingDeleteId = it }
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onEdit = { record -> openEditor(record.event.typeCode, record) },
                    onDelete = { pendingDeleteId = it }
                )
            }
            composable("statistics") { StatisticsScreen(viewModel = viewModel) }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenModules = { navController.navigate("modules") }
                )
            }
            composable("modules") {
                ModuleManagementScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (editorVisible) {
        when (editorTypeCode) {
            WaterFields.TYPE_CODE -> WaterEditorDialog(
                record = editingRecord,
                onDismiss = { editorVisible = false },
                onSave = { draft ->
                    viewModel.saveGenericRecord(editingRecord?.event?.id, draft)
                    editorVisible = false
                }
            )
            CoffeeFields.TYPE_CODE -> CoffeeEditorDialog(
                record = editingRecord,
                onDismiss = { editorVisible = false },
                onSave = { draft ->
                    viewModel.saveGenericRecord(editingRecord?.event?.id, draft)
                    editorVisible = false
                }
            )
            SportFields.TYPE_CODE -> SportEditorDialog(
                record = editingRecord,
                onDismiss = { editorVisible = false },
                onSave = { draft ->
                    viewModel.saveGenericRecord(editingRecord?.event?.id, draft)
                    editorVisible = false
                }
            )
            else -> PoopEditorDialog(
                record = editingRecord?.toPoopRelation(),
                onDismiss = { editorVisible = false },
                onSave = { draft ->
                    viewModel.saveRecord(editingRecord?.event?.id, draft)
                    editorVisible = false
                }
            )
        }
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
