@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.lifetrack.feature.poop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.example.lifetrack.domain.model.PoopRecord
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.core.ui.LifeTrackTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val poopItemTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 首页 / 历史中的排便记录卡：暖棕识别色、关键字段摘要，编辑与删除保持独立点击区。 */
@Composable
fun PoopRecordItem(
    record: PoopRecord,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = LifeTrackTokens.ModuleColors.of(PoopFields.TYPE_CODE)
    val detail = record.detail
    val timeLabel = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(poopItemTimeFormatter)
    }.getOrDefault("时间未知")

    val tags = listOfNotNull(
        detail?.bristolType?.let { "布里斯托 $it 型" },
        detail?.amountCode?.let { code ->
            when (code) {
                1 -> "少量"
                2 -> "适中"
                3 -> "大量"
                else -> null
            }
        },
        detail?.effortCode?.let { code ->
            when (code) {
                1 -> "不费力"
                2 -> "轻微费力"
                3 -> "明显费力"
                else -> null
            }
        },
        detail?.durationMinutes?.let(PoopFields::durationLabel),
        detail?.urgency?.let { if (it) "有便意急迫感" else "无便意急迫感" },
        detail?.abdominalDiscomfort?.let { if (it) "有腹部不适" else "无腹部不适" },
        detail?.incompleteEvacuation?.let { if (it) "有排不净感" else "无排不净感" }
    )
    val note = record.event.note.orEmpty()

    Card(
        onClick = onShowDetail,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Loop,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "排便记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑记录")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "删除记录")
                    }
                }
            }

            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(accent.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "尚未填写形态或感受",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (note.isNotBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
