package com.example.lifetrack.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.feature.poop.PoopRecordItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    onQuickRecord: () -> Unit,
    onDetailedRecord: () -> Unit,
    onEdit: (PoopRecordRelation) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.todayRecords.collectAsStateWithLifecycle()
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"))
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今天", style = MaterialTheme.typography.headlineMedium)
                Text(today, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("今日排便次数", style = MaterialTheme.typography.titleMedium)
                    Text(records.size.toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onQuickRecord, modifier = Modifier.weight(1f)) { Text("快速打卡") }
                OutlinedButton(onClick = onDetailedRecord, modifier = Modifier.weight(1f)) { Text("详细记录") }
            }
            Text(
                "快速打卡会立即保存当前时间；详细记录可以补充形态和身体感受。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item { Text("今日记录", style = MaterialTheme.typography.titleLarge) }
        if (records.isEmpty()) {
            item {
                Text("今天还没有记录。完成第一次打卡后，会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(records, key = { it.event.id }) { record ->
                PoopRecordItem(
                    record = record,
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record.event.id) }
                )
            }
        }
    }
}
