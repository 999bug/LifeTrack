package com.example.lifetrack.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.record.RecordRow
import com.example.lifetrack.feature.statistics.StatsColors
import com.example.lifetrack.feature.statistics.rememberStatsColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onEdit: (BackupRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.historyRecords.collectAsStateWithLifecycle()
    val query by viewModel.historyQuery.collectAsStateWithLifecycle()
    val calendar by viewModel.calendar.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    var calendarMode by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val colors = rememberStatsColors()

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
            ViewSwitcher(
                calendarMode = calendarMode,
                onChange = { calendarMode = it },
                colors = colors
            )
        }
        if (calendarMode) {
            item {
                CalendarMonthView(
                    month = calendar,
                    today = today,
                    canGoNext = calendar.start.isBefore(today.withDayOfMonth(1)),
                    onPrevious = { viewModel.shiftCalendarMonth(-1) },
                    onNext = { viewModel.shiftCalendarMonth(1) },
                    onSelectDate = { date ->
                        viewModel.setHistoryFilter(date, date)
                        calendarMode = false
                    }
                )
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
                RecordRow(
                    record = record,
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record.event.id) }
                )
            }
        }
        if (!query.filtered && !calendarMode) {
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

/** 与统计页周期胶囊同构的两态切换，不新增底部 tab。 */
@Composable
private fun ViewSwitcher(
    calendarMode: Boolean,
    onChange: (Boolean) -> Unit,
    colors: StatsColors
) {
    // 与统计页周期切换同构：大字号抬高整条，避免 13sp → 26sp 后被裁掉。
    val barHeight = if (LocalDensity.current.fontScale > 1.3f) 52.dp else 38.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(barHeight / 2))
    ) {
        listOf(false to "列表", true to "月历").forEach { (mode, label) ->
            val selected = calendarMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .clip(RoundedCornerShape((barHeight - 6.dp) / 2))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onChange(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = if (selected) colors.onAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
