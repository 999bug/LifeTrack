@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.lifetrack.feature.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.example.lifetrack.core.model.PickerDateMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

/** 通用编辑器壳产出的「事件」部分；各类型自己的详情字段由调用方组装进 draft。 */
data class RecordEditorValue(
    val occurredAtEpochMillis: Long,
    val zoneId: String,
    val note: String
)

/**
 * 所有记录类型共用的编辑器壳：日期 / 时间 / 时区提示 / 备注 / 保存取消。
 *
 * [content] 是详情插槽，由各类型提供；状态仍由调用方持有，壳层不认识任何领域草稿类型。
 * [resetKey] 变化（例如从「新增」切到「编辑」）时重新初始化全部状态。
 */
@Composable
fun RecordEditorDialog(
    title: String,
    resetKey: Any?,
    onDismiss: () -> Unit,
    onSave: (RecordEditorValue) -> Unit,
    initialMillis: Long? = null,
    initialZoneId: String? = null,
    initialNote: String = "",
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val recordZone = remember(resetKey) {
        runCatching { ZoneId.of(initialZoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initialDateTime = remember(resetKey) {
        val millis = initialMillis ?: System.currentTimeMillis()
        Instant.ofEpochMilli(millis).atZone(recordZone).toLocalDateTime()
    }
    var selectedDate by remember(resetKey) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember(resetKey) { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by remember(resetKey) { mutableIntStateOf(initialDateTime.minute) }
    var note by remember(resetKey) { mutableStateOf(initialNote) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                content()
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
            TextButton(
                enabled = saveEnabled,
                onClick = {
                    val localDateTime = LocalDateTime.of(selectedDate, LocalTime.of(selectedHour, selectedMinute))
                    onSave(
                        RecordEditorValue(
                            occurredAtEpochMillis = localDateTime.atZone(recordZone).toInstant().toEpochMilli(),
                            zoneId = recordZone.id,
                            note = note.trim()
                        )
                    )
                }
            ) { Text("保存") }
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
