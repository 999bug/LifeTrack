package com.example.lifetrack.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.poop.PoopRecordItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onEdit: (PoopRecordRelation) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.historyRecords.collectAsStateWithLifecycle()
    val query by viewModel.historyQuery.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }

    val grouped = records.groupBy { it.event.localDate }.toSortedMap(compareByDescending { it })

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "历史记录",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showRangePicker = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("筛选")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatRange(query)} · 共 ${records.size} 条",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (query.filtered) {
                    TextButton(onClick = viewModel::clearHistoryFilter) { Text("清除筛选") }
                }
            }
        }
        if (records.isEmpty()) {
            item {
                Text(
                    text = if (query.filtered) "这个日期范围内还没有记录。" else "还没有历史记录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        grouped.forEach { (date, dailyRecords) ->
            item(key = "header-$date") {
                val label = runCatching {
                    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
                }.getOrDefault(date)
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(dailyRecords, key = { it.event.id }) { record ->
                PoopRecordItem(
                    record = record,
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record.event.id) }
                )
            }
        }
        if (!query.filtered) {
            item {
                TextButton(
                    onClick = viewModel::loadEarlierHistory,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("加载更早 30 天") }
            }
        }
    }

    if (showRangePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = PickerDateMapper.toUtcMillis(query.start),
            initialSelectedEndDateMillis = PickerDateMapper.toUtcMillis(query.end)
        )
        val start = state.selectedStartDateMillis
        val end = state.selectedEndDateMillis
        // DateRangePicker 在受限高度里不会自己滚动，会被对话框裁掉下半月，因此用全屏对话框
        Dialog(
            onDismissRequest = { showRangePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    Text(
                        "选择日期范围",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp)
                    )
                    DateRangePicker(
                        state = state,
                        modifier = Modifier.weight(1f),
                        title = {},
                        showModeToggle = false
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRangePicker = false }) { Text("取消") }
                        TextButton(
                            enabled = start != null && end != null,
                            onClick = {
                                if (start != null && end != null) {
                                    viewModel.setHistoryFilter(
                                        PickerDateMapper.fromUtcMillis(start),
                                        PickerDateMapper.fromUtcMillis(end)
                                    )
                                    showRangePicker = false
                                }
                            }
                        ) { Text("应用") }
                    }
                }
            }
        }
    }
}

private fun formatRange(query: HistoryQuery): String =
    if (query.start.year == query.end.year) {
        "${query.start.format(monthDayFormatter)} — ${query.end.format(monthDayFormatter)}"
    } else {
        "${query.start.format(fullDateFormatter)} — ${query.end.format(fullDateFormatter)}"
    }
