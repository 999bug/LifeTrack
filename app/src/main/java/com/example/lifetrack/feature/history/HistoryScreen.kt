package com.example.lifetrack.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.poop.PoopRecordItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onEdit: (PoopRecordRelation) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.historyRecords.collectAsStateWithLifecycle()
    val grouped = records.groupBy { it.event.localDate }.toSortedMap(compareByDescending { it })
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("历史记录", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text("展示最近 30 天的排便记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (records.isEmpty()) {
            item { Text("还没有历史记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        grouped.forEach { (date, dailyRecords) ->
            item(key = "header-$date") {
                val label = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")) }.getOrDefault(date)
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
    }
}
