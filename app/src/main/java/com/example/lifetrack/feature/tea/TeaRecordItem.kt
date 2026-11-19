package com.example.lifetrack.feature.tea

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
import androidx.compose.material.icons.filled.Spa
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
import com.example.lifetrack.core.model.TeaFields
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val teaItemTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class TeaItemColors(
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color
)

@Composable
private fun teaItemColors(): TeaItemColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        TeaItemColors(
            accent = Color(0xFFA8C3A3),
            accentContainer = Color(0xFF27362B),
            onAccentContainer = Color(0xFFD7E8D7)
        )
    } else {
        TeaItemColors(
            accent = Color(0xFF4F715A),
            accentContainer = Color(0xFFE6EEE5),
            onAccentContainer = Color(0xFF294732)
        )
    }

@Composable
fun TeaRecordItem(
    record: BackupRecord,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val fields = (record.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val variety = fields[TeaFields.VARIETY] ?: "茶"
    val summary = buildList {
        fields[TeaFields.CUPS]?.let { add("${it} 杯") }
        fields[TeaFields.AMOUNT]?.let { add("${it} ml") }
        fields[TeaFields.CALORIES]?.let { add("$it 大卡") }
        if (record.event.note.isNotBlank()) add(record.event.note)
    }.joinToString(" · ").ifBlank { "尚未填写详情" }
    val timeLabel = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(teaItemTimeFormatter)
    }.getOrDefault("时间未知")
    val colors = teaItemColors()
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
                            Icons.Filled.Spa,
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
                                variety,
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
                    TeaItemActions(onEdit = onEdit, onDelete = onDelete)
                }
            }
            if (bigFont) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TeaItemActions(onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun TeaItemActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    Row {
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "编辑茶记录")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除茶记录")
        }
    }
}
