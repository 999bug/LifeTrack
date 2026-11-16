package com.example.lifetrack.feature.intimacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.feature.statistics.CardShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

/**
 * 经期子应用里的亲密关系区（放在「流量」一栏上方）。
 *
 * 亲密关系是按天的独立事件（一天可以多次），与月经的「一天一条体征」不同，
 * 所以不塞进 [com.example.lifetrack.feature.menstruation.PeriodDayForm] 的字段行，
 * 而是单独一张卡：标题带当天次数，行内「第 N 次 · 时间 · 措施」，右侧删除，点行改时间 / 措施。
 *
 * 隐私口径：措施文案只在这一页出现（已经进入高敏感子应用）；首页时间轴与历史列表对
 * 亲密关系仍只显示「已记录」，不把措施拼进通用摘要。
 */
@Composable
fun IntimacyDaySection(
    records: List<BackupRecord>,
    readOnly: Boolean,
    moduleColor: Color,
    onAdd: () -> Unit,
    onEdit: (BackupRecord) -> Unit,
    onDelete: (BackupRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(moduleColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = moduleColor,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("亲密关系", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (records.isEmpty()) {
                        "这一天还没有记录"
                    } else {
                        "已记录 ${records.size} 次 · 点行可改时间或措施"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!readOnly) {
                TextButton(onClick = onAdd) { Text("记一次") }
            }
        }

        if (records.isNotEmpty()) {
            records.forEachIndexed { index, record ->
                IntimacyDayRow(
                    ordinal = index + 1,
                    record = record,
                    moduleColor = moduleColor,
                    readOnly = readOnly,
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record) }
                )
            }
        }
    }
}

@Composable
private fun IntimacyDayRow(
    ordinal: Int,
    record: BackupRecord,
    moduleColor: Color,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeLabel = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(timeFormatter)
    }.getOrDefault("时间未知")
    val method = (record.detail as? RecordDetail.Generic)?.fields?.get(IntimacyFields.METHOD)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !readOnly, onClick = onEdit)
            .semantics {
                contentDescription = "第 $ordinal 次亲密记录，$timeLabel，${labelOf(method)}"
            }
            .padding(start = 2.dp, top = 2.dp, bottom = 2.dp, end = if (readOnly) 2.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "第 $ordinal 次",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = timeLabel, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = labelOf(method),
            style = MaterialTheme.typography.bodySmall,
            color = moduleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!readOnly) {
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "删除第 $ordinal 次亲密记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 措施文案：新记录看 `method`；只有旧版字段的记录退回「保护措施」口径，不假装没记过。 */
private fun labelOf(method: String?): String {
    if (method != null) return IntimacyFields.methodLabel(method)
    return "措施未记录"
}
