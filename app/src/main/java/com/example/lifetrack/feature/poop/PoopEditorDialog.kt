package com.example.lifetrack.feature.poop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.feature.record.ChoiceRow
import com.example.lifetrack.feature.record.RecordEditorDialog
import com.example.lifetrack.feature.record.TriStateRow

/**
 * 排便详情编辑器：通用事件壳 + 本类型专属的六个详情字段。
 * 对外签名保持不变，导航与 ViewModel 无需感知编辑器重构。
 */
@Composable
fun PoopEditorDialog(
    record: PoopRecordRelation?,
    onDismiss: () -> Unit,
    onSave: (PoopRecordDraft) -> Unit
) {
    var bristol by remember(record) { mutableStateOf(record?.detail?.bristolType) }
    var amount by remember(record) { mutableStateOf(record?.detail?.amountCode) }
    var effort by remember(record) { mutableStateOf(record?.detail?.effortCode) }
    var urgency by remember(record) { mutableStateOf(record?.detail?.urgency) }
    var abdominal by remember(record) { mutableStateOf(record?.detail?.abdominalDiscomfort) }
    var incomplete by remember(record) { mutableStateOf(record?.detail?.incompleteEvacuation) }

    RecordEditorDialog(
        title = if (record == null) "详细记录" else "编辑排便记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        onDismiss = onDismiss,
        onSave = { value ->
            onSave(
                PoopRecordDraft(
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    bristolType = bristol,
                    amountCode = amount,
                    effortCode = effort,
                    urgency = urgency,
                    abdominalDiscomfort = abdominal,
                    incompleteEvacuation = incomplete,
                    note = value.note
                )
            )
        }
    ) {
        Text("布里斯托类型（可选）", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..7).forEach { value ->
                FilterChip(
                    selected = bristol == value,
                    onClick = { bristol = if (bristol == value) null else value },
                    label = { Text("$value 型") }
                )
            }
        }
        Text("排便量（可选）", style = MaterialTheme.typography.titleSmall)
        ChoiceRow(
            labels = listOf("少量", "适中", "大量"),
            selected = amount,
            values = listOf(1, 2, 3),
            onSelect = { amount = it }
        )
        Text("费力程度（可选）", style = MaterialTheme.typography.titleSmall)
        ChoiceRow(
            labels = listOf("不费力", "轻微", "明显"),
            selected = effort,
            values = listOf(1, 2, 3),
            onSelect = { effort = it }
        )
        TriStateRow("便意急迫感", urgency) { urgency = it }
        TriStateRow("腹部不适", abdominal) { abdominal = it }
        TriStateRow("排不净感", incomplete) { incomplete = it }
    }
}
