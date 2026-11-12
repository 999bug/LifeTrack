package com.example.lifetrack.feature.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.model.toEntity
import com.example.lifetrack.feature.coffee.CoffeeRecordItem
import com.example.lifetrack.feature.intimacy.IntimacyRecordItem
import com.example.lifetrack.feature.menstruation.MenstruationRecordItem
import com.example.lifetrack.feature.poop.PoopRecordItem
import com.example.lifetrack.feature.sport.SportRecordItem
import com.example.lifetrack.feature.water.WaterRecordItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 一条记录的统一渲染入口：按 typeCode 分派到各类型卡片。
 * 今天页与历史页共用，避免两个页面各写一套分派后出现「历史里看不到运动」这类口径漂移。
 * 未知类型（例如备份来自更高版本）只读展示 + 允许删除，不假装能编辑。
 */
@Composable
fun RecordRow(
    record: BackupRecord,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    when (record.event.typeCode) {
        DEFAULT_POOP_TYPE.code -> PoopRecordItem(
            record = PoopRecordRelation(
                event = record.event,
                detail = (record.detail as? RecordDetail.Poop)?.toEntity(record.event.id)
            ),
            onShowDetail = onShowDetail,
            onEdit = onEdit,
            onDelete = onDelete
        )

        WaterFields.TYPE_CODE -> WaterRecordItem(
            record = record,
            onShowDetail = onShowDetail,
            onEdit = onEdit,
            onDelete = onDelete
        )
        CoffeeFields.TYPE_CODE -> CoffeeRecordItem(
            record = record,
            onShowDetail = onShowDetail,
            onEdit = onEdit,
            onDelete = onDelete
        )
        SportFields.TYPE_CODE -> SportRecordItem(
            record = record,
            onShowDetail = onShowDetail,
            onEdit = onEdit,
            onDelete = onDelete
        )
        IntimacyFields.TYPE_CODE -> IntimacyRecordItem(
            record = record,
            onShowDetail = onShowDetail,
            onEdit = onEdit,
            onDelete = onDelete
        )
        MenstruationFields.TYPE_CODE -> MenstruationRecordItem(
            record = record,
            onShowDetail = onShowDetail,
            onEdit = onEdit,
            onDelete = onDelete
        )
        else -> UnknownRecordItem(
            record = record,
            onShowDetail = onShowDetail,
            onDelete = onDelete
        )
    }
}

@Composable
private fun UnknownRecordItem(
    record: BackupRecord,
    onShowDetail: () -> Unit,
    onDelete: () -> Unit
) {
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
