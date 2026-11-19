package com.example.lifetrack.feature.coffee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.RecordDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun CoffeeRecordItem(
    record: BackupRecord,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val fields = (record.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val variety = fields[CoffeeFields.VARIETY]
    val amount = fields[CoffeeFields.AMOUNT]
    val title = variety ?: CoffeeFields.drinkLabel(fields[CoffeeFields.DRINK].orEmpty()).let {
        if (it.isBlank()) "咖啡" else "旧咖啡记录"
    }
    val summary = buildList {
        fields[CoffeeFields.SIZE]?.let { add(CoffeeFields.sizeLabel(it)) }
        fields[CoffeeFields.TEMPERATURE]?.let { add(CoffeeFields.temperatureLabel(it)) }
        amount?.let { add("${it}ml") }
        fields[CoffeeFields.BEANS]?.let { add(CoffeeFields.beansLabel(it)) }
        fields[CoffeeFields.SUGAR]?.let { add(CoffeeFields.sugarLabel(it)) }
        fields[CoffeeFields.CALORIES]?.let { add("$it 大卡") }
        if (record.event.note.isNotBlank()) add(record.event.note)
    }.joinToString(" · ")

    val timeLabel = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(timeFormatter)
    }.getOrDefault("时间未知")

    Card(
        onClick = onShowDetail,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(timeLabel, style = MaterialTheme.typography.titleMedium)
                    Text(title, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    summary.ifBlank { "尚未填写详情" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑咖啡记录")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "删除咖啡记录")
                }
            }
        }
    }
}
