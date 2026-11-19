package com.example.lifetrack.feature.intimacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.RecordDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val daySectionTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

/**
 * 经期子应用里的亲密关系区。
 *
 * 亲密记录按天独立计数，行内只显示次序、时间与措施；备注和其他详情仍需进入编辑器查看。
 * 视觉沿用梅紫模块色，与经期砖玫瑰保持区分。
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
    val largeFont = LocalDensity.current.fontScale > 1.3f
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntimacyDayHeader(
                        count = records.size,
                        moduleColor = moduleColor
                    )
                    if (!readOnly) {
                        TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                            Text("记一次")
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IntimacyDayHeader(
                        count = records.size,
                        moduleColor = moduleColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (!readOnly) {
                        TextButton(onClick = onAdd) { Text("记一次") }
                    }
                }
            }

            if (records.isEmpty()) {
                Text(
                    text = "这一天还没有记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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
}

@Composable
private fun IntimacyDayHeader(
    count: Int,
    moduleColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(moduleColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = moduleColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("亲密关系", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (count == 0) "私密记录 · 仅本机保存" else "已记录 $count 次 · 点行可改时间或措施",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
            .format(daySectionTimeFormatter)
    }.getOrDefault("时间未知")
    val method = (record.detail as? RecordDetail.Generic)?.fields?.get(IntimacyFields.METHOD)
    val methodLabel = labelOf(method)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !readOnly, onClick = onEdit)
            .semantics {
                contentDescription = "第 $ordinal 次亲密记录，$timeLabel，$methodLabel"
            },
        shape = RoundedCornerShape(12.dp),
        color = moduleColor.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 11.dp, top = 7.dp, bottom = 7.dp, end = if (readOnly) 11.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(moduleColor)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "第 $ordinal 次 · $timeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = methodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = moduleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!readOnly) {
                IconButton(onClick = onDelete, modifier = Modifier.size(42.dp)) {
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
}

/** 措施文案：新记录看 `method`；只有旧版字段的记录退回「保护措施」口径，不假装没记过。 */
private fun labelOf(method: String?): String {
    if (method != null) return IntimacyFields.methodLabel(method)
    return "措施未记录"
}
