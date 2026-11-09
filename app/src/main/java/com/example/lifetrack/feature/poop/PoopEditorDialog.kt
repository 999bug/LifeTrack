@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.lifetrack.feature.poop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.domain.model.PoopRecordDraft
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

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
    val initialDateTime = remember(record) {
        val millis = record?.event?.occurredAtEpochMillis ?: System.currentTimeMillis()
        Instant.ofEpochMilli(millis).atZone(recordZone).toLocalDateTime()
    }
    var selectedDate by remember(record) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember(record) { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by remember(record) { mutableIntStateOf(initialDateTime.minute) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var bristol by remember(record) { mutableStateOf(record?.detail?.bristolType) }
    var amount by remember(record) { mutableStateOf(record?.detail?.amountCode) }
    var effort by remember(record) { mutableStateOf(record?.detail?.effortCode) }
    var urgency by remember(record) { mutableStateOf(record?.detail?.urgency) }
    var abdominal by remember(record) { mutableStateOf(record?.detail?.abdominalDiscomfort) }
    var incomplete by remember(record) { mutableStateOf(record?.detail?.incompleteEvacuation) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (record == null) "详细记录" else "编辑排便记录") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val dateText = selectedDate.format(dateFormatter)
                val timeText = String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)
                if (LocalDensity.current.fontScale > 1.3f) {
                    // 大字号下并排会把「2026年11月9日」截断，改成上下两行各占满宽
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField(
                            label = "日期",
                            value = dateText,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showDatePicker = true }
                        )
                        PickerField(
                            label = "时间",
                            value = timeText,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showTimePicker = true }
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField(
                            label = "日期",
                            value = dateText,
                            modifier = Modifier.weight(1f),
                            onClick = { showDatePicker = true }
                        )
                        PickerField(
                            label = "时间",
                            value = timeText,
                            modifier = Modifier.weight(1f),
                            onClick = { showTimePicker = true }
                        )
                    }
                }
                Text(
                    "按记录所在地时区保存：${recordZone.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val localDateTime = LocalDateTime.of(selectedDate, LocalTime.of(selectedHour, selectedMinute))
                onSave(
                    PoopRecordDraft(
                        occurredAtEpochMillis = localDateTime.atZone(recordZone).toInstant().toEpochMilli(),
                        zoneId = recordZone.id,
                        bristolType = bristol,
                        amountCode = amount,
                        effortCode = effort,
                        urgency = urgency,
                        abdominalDiscomfort = abdominal,
                        incompleteEvacuation = incomplete,
                        note = note.trim()
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = PickerDateMapper.toUtcMillis(selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = PickerDateMapper.fromUtcMillis(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        TimeDialog(
            state = state,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                selectedHour = state.hour
                selectedMinute = state.minute
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        // readOnly 的输入框仍然会吃掉点击，用一层透明可点区域接管
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClick = onClick)
        )
    }
}

@Composable
private fun TimeDialog(
    state: TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("选择时间", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onConfirm) { Text("确定") }
                }
            }
        }
    }
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
