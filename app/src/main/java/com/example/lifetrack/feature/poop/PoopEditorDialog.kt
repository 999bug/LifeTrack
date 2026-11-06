package com.example.lifetrack.feature.poop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.domain.model.PoopRecordDraft
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val editorFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")

@Composable
fun PoopEditorDialog(
    record: PoopRecordRelation?,
    onDismiss: () -> Unit,
    onSave: (PoopRecordDraft) -> Unit
) {
    val recordZone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initialTime = remember(record) {
        val millis = record?.event?.occurredAtEpochMillis ?: System.currentTimeMillis()
        Instant.ofEpochMilli(millis).atZone(recordZone).format(editorFormatter)
    }
    var timeText by remember(record) { mutableStateOf(initialTime) }
    var bristol by remember(record) { mutableStateOf(record?.detail?.bristolType) }
    var amount by remember(record) { mutableStateOf(record?.detail?.amountCode) }
    var effort by remember(record) { mutableStateOf(record?.detail?.effortCode) }
    var urgency by remember(record) { mutableStateOf(record?.detail?.urgency) }
    var abdominal by remember(record) { mutableStateOf(record?.detail?.abdominalDiscomfort) }
    var incomplete by remember(record) { mutableStateOf(record?.detail?.incompleteEvacuation) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }
    var timeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (record == null) "详细记录" else "编辑排便记录") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it; timeError = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("排便时间") },
                    supportingText = { Text("格式：yyyy-MM-dd HH:mm") },
                    isError = timeError,
                    singleLine = true
                )
                Text("布里斯托类型（可选）", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    minLines = 2
                )
                if (timeError) {
                    Text("时间格式不正确，请按 yyyy-MM-dd HH:mm 填写。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val localDateTime = runCatching { LocalDateTime.parse(timeText, editorFormatter) }.getOrNull()
                if (localDateTime == null) {
                    timeError = true
                } else {
                    val zone = recordZone
                    val instant = localDateTime.atZone(zone).toInstant()
                    onSave(
                        PoopRecordDraft(
                            occurredAtEpochMillis = instant.toEpochMilli(),
                            zoneId = zone.id,
                            bristolType = bristol,
                            amountCode = amount,
                            effortCode = effort,
                            urgency = urgency,
                            abdominalDiscomfort = abdominal,
                            incompleteEvacuation = incomplete,
                            note = note.trim()
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ChoiceRow(
    labels: List<String>,
    values: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("未记录") })
        labels.zip(values).forEach { (label, value) ->
            FilterChip(selected = selected == value, onClick = { onSelect(if (selected == value) null else value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun TriStateRow(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text("未记录") })
            FilterChip(selected = value == false, onClick = { onChange(false) }, label = { Text("否") })
            FilterChip(selected = value == true, onClick = { onChange(true) }, label = { Text("是") })
        }
    }
}
