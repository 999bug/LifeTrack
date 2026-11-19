package com.example.lifetrack.feature.water

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.WaterFields
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val waterItemTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class WaterItemColors(
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color
)

@Composable
private fun waterItemColors(): WaterItemColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        WaterItemColors(
            accent = Color(0xFF8FC0D8),
            accentContainer = Color(0xFF1D313A),
            onAccentContainer = Color(0xFFC8E7F2)
        )
    } else {
        WaterItemColors(
            accent = Color(0xFF3D7391),
            accentContainer = Color(0xFFE4EFF5),
            onAccentContainer = Color(0xFF21485B)
        )
    }

@Composable
fun WaterRecordItem(
    record: BackupRecord,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val fields = (record.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val amount = fields[WaterFields.AMOUNT]
    val unit = WaterFields.unitLabel(fields[WaterFields.UNIT].orEmpty())
    val summary = buildList {
        amount?.let { add("$it $unit") }
        if (record.event.note.isNotBlank()) add(record.event.note)
    }.joinToString(" · ").ifBlank { "尚未填写详情" }
    val timeLabel = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(waterItemTimeFormatter)
    }.getOrDefault("时间未知")
    val colors = waterItemColors()
    val bigFont = LocalDensity.current.fontScale > 1.3f

    Card(
        onClick = onShowDetail,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = colors.accentContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = colors.accent
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(timeLabel, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = colors.accentContainer
                        ) {
                            Text(
                                "饮水",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onAccentContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!bigFont) {
                    WaterItemActions(onEdit = onEdit, onDelete = onDelete)
                }
            }
            if (bigFont) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    WaterItemActions(onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun WaterItemActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    Row {
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "编辑饮水记录")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除饮水记录")
        }
    }
}
