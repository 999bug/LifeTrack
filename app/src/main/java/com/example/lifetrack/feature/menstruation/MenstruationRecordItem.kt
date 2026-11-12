package com.example.lifetrack.feature.menstruation

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")

/**
 * A9 L1 收敛：列表只暴露类型与日期，不拼流量 / 症状等字段值，也不拼日记原文；
 * 文字区读屏合并成一句汇总，编辑 / 删除按钮保持独立可操作。
 * 完整字段由用户主动点开详情弹窗后展示，与亲密关系同一套收敛口径。
 */
@Composable
fun MenstruationRecordItem(
    record: BackupRecord,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(dateFormatter)
    }.getOrDefault("日期未知")

    Card(
        onClick = onShowDetail,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = "已记录一条月经记录" }
            ) {
                Text(dateLabel, style = MaterialTheme.typography.titleMedium)
                Text("月经", color = MaterialTheme.colorScheme.primary)
                Text(
                    "详情需点开查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑月经记录")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "删除月经记录")
                }
            }
        }
    }
}