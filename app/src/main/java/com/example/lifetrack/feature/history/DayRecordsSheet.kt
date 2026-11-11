package com.example.lifetrack.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.record.RecordSummary
import java.time.LocalDate

private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 日期详情：点月历某一格后，列出当天的全部记录。
 *
 * 只读概览——每行是「时间 + 类型 + 一行摘要」，不提供编辑与删除，
 * 避免在弹层里再套一层记录详情。要改内容请回列表页操作。
 * 亲密关系沿用 L1 收敛：摘要固定为「已记录」，不展示任何字段值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayRecordsSheet(
    date: LocalDate,
    records: List<BackupRecord>,
    typeNames: Map<String, String>,
    onDismiss: () -> Unit
) {
    val title = "${date.monthValue}月${date.dayOfMonth}日 · 周${WEEK_LABELS[date.dayOfWeek.value - 1]}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (records.isNotEmpty()) {
                    Text(
                        text = "${records.size} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (records.isEmpty()) {
                Text(
                    text = "这天没有记录。可在新增记录时把时间改到这一天。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            } else {
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(records, key = { it.event.id }) { record ->
                        DayRecordLine(record = record, typeNames = typeNames)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
            ) { Text("关闭") }
        }
    }
}

@Composable
private fun DayRecordLine(record: BackupRecord, typeNames: Map<String, String>) {
    val time = RecordSummary.time(record)
    val typeName = RecordSummary.typeName(record.event.typeCode, typeNames)
    val detail = RecordSummary.detail(record)
    val dotColor = LifeTrackTokens.ModuleColors.of(record.event.typeCode)
    // 一行念完，不拆成「时间」「类型」「摘要」三个互不相关的节点。
    val lineSemantics = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "$time，$typeName，$detail"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(lineSemantics)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = typeName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
