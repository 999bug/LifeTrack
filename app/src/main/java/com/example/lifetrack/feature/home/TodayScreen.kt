package com.example.lifetrack.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.model.toEntity
import com.example.lifetrack.feature.poop.PoopRecordItem
import com.example.lifetrack.feature.water.WaterRecordItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    onQuickRecord: () -> Unit,
    onDetailedRecord: () -> Unit,
    onWaterRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (BackupRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.todayRecords.collectAsStateWithLifecycle()
    val trackTypes by viewModel.trackTypes.collectAsStateWithLifecycle()
    val enabled = trackTypes.filter { it.enabled }.mapTo(HashSet()) { it.code }
    val canRecordPoop = DEFAULT_POOP_TYPE.code in enabled
    val canRecordWater = WaterFields.TYPE_CODE in enabled
    // 统计、历史、月历目前仍只统计排便，这里的次数卡必须同口径，不能把饮水算进去。
    val poopCount = records.count { it.event.typeCode == DEFAULT_POOP_TYPE.code }
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
                    Text(poopCount.toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item {
            if (!canRecordPoop && !canRecordWater) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("当前没有启用的记录项目", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "可在设置 → 记录项目中启用后继续记录。已有的历史记录不受影响。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onOpenSettings) { Text("去设置") }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (canRecordPoop) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onQuickRecord, modifier = Modifier.weight(1f)) { Text("快速打卡") }
                            OutlinedButton(onClick = onDetailedRecord, modifier = Modifier.weight(1f)) { Text("详细记录") }
                        }
                    }
                    if (canRecordWater) {
                        OutlinedButton(
                            onClick = onWaterRecord,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("记录饮水") }
                    }
                    Text(
                        "快速打卡会立即保存当前时间；详细记录可补形态与身体感受，记录饮水需填写数量。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { Text("今日记录", style = MaterialTheme.typography.titleLarge) }
        if (records.isEmpty()) {
            item {
                Text("今天还没有记录。完成第一次打卡后，会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(records, key = { it.event.id }) { record ->
                when (record.event.typeCode) {
                    DEFAULT_POOP_TYPE.code -> PoopRecordItem(
                        record = PoopRecordRelation(
                            event = record.event,
                            detail = (record.detail as? RecordDetail.Poop)?.toEntity(record.event.id)
                        ),
                        onEdit = { onEdit(record) },
                        onDelete = { onDelete(record.event.id) }
                    )
                    WaterFields.TYPE_CODE -> WaterRecordItem(
                        record = record,
                        onEdit = { onEdit(record) },
                        onDelete = { onDelete(record.event.id) }
                    )
                    else -> UnknownRecordItem(
                        record = record,
                        onDelete = { onDelete(record.event.id) }
                    )
                }
            }
        }
    }
}

/** 备份可能带入当前版本没有编辑器的类型：只读展示 + 允许删除，不假装能编辑。 */
@Composable
private fun UnknownRecordItem(record: BackupRecord, onDelete: () -> Unit) {
    val timeLabel = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(timeFormatter)
    }.getOrDefault("时间未知")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(timeLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildList {
                        add(record.event.typeCode)
                        if (record.event.note.isNotBlank()) add(record.event.note)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "删除记录")
            }
        }
    }
}
