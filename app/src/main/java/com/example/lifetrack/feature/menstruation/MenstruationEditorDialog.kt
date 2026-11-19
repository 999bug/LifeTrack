@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.ChoiceChipRow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NO_ENTRY = ""
private val editorDateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

/**
 * 月经编辑器：首页时间轴 / 历史页 / 记录列表点编辑时走这里。
 *
 * 保持原函数签名与 [MenstruationFields] 字段口径不变，只把通用 AlertDialog 换成
 * 全屏摘要式编辑页。日期、时间、时区、详情与日记仍组成同一条 [RecordDraft]；
 * 五项详情均可留空，日记继续写入 `note`。
 */
@Composable
fun MenstruationEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit,
    initialDate: LocalDate? = null
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    var flow by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.FLOW]) }
    var pain by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.PAIN]) }
    var mood by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.MOOD]) }
    var discharge by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.DISCHARGE]) }
    var symptoms by remember(record) {
        mutableStateOf(MenstruationFields.decodeSymptoms(initial[MenstruationFields.SYMPTOMS].orEmpty()))
    }

    val recordZone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initialDateTime = remember(record, initialDate) {
        val millis = record?.event?.occurredAtEpochMillis
            ?: initialDate?.atTime(LocalTime.NOON)?.atZone(recordZone)?.toInstant()?.toEpochMilli()
            ?: System.currentTimeMillis()
        Instant.ofEpochMilli(millis).atZone(recordZone).toLocalDateTime()
    }
    var selectedDate by remember(record, initialDate) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember(record, initialDate) { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by remember(record, initialDate) { mutableIntStateOf(initialDateTime.minute) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val palette = rememberPeriodPalette()
    val dateText = selectedDate.format(editorDateFormatter)
    val timeText = String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)
    val filledCount = listOfNotNull(flow, pain, mood, discharge).size +
        (if (symptoms.isNotEmpty()) 1 else 0) +
        (if (note.isNotBlank()) 1 else 0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
            ) {
                EditorTopBar(
                    title = if (record == null) "记录经期" else "编辑经期",
                    accent = palette.accent,
                    onClose = onDismiss
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        EditorHero(
                            dateText = dateText,
                            timeText = timeText,
                            filledCount = filledCount,
                            palette = palette
                        )
                    }
                    item {
                        EditorDateTimeCard(
                            dateText = dateText,
                            timeText = timeText,
                            zoneId = recordZone.id,
                            onDateClick = { showDatePicker = true },
                            onTimeClick = { showTimePicker = true }
                        )
                    }
                    item {
                        EditorOptionSection(title = "流量", hint = "选择最接近的一档") {
                            ChoiceChipRow(
                                values = listOf(NO_ENTRY) + MenstruationFields.FLOW_VALUES,
                                selected = flow ?: NO_ENTRY,
                                label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.flowLabel(it) },
                                onSelect = { flow = it.takeIf { selected -> selected != NO_ENTRY } }
                            )
                        }
                    }
                    item {
                        EditorOptionSection(title = "痛经", hint = "按今天的实际感受记录") {
                            ChoiceChipRow(
                                values = listOf(NO_ENTRY) + MenstruationFields.PAIN_VALUES,
                                selected = pain ?: NO_ENTRY,
                                label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.painLabel(it) },
                                onSelect = { pain = it.takeIf { selected -> selected != NO_ENTRY } }
                            )
                        }
                    }
                    item {
                        EditorOptionSection(
                            title = "症状",
                            hint = "可多选，最多 ${MenstruationFields.MAX_SYMPTOMS} 项"
                        ) {
                            SymptomChips(
                                selected = symptoms,
                                onToggle = { code ->
                                    symptoms = when {
                                        code in symptoms -> symptoms - code
                                        symptoms.size < MenstruationFields.MAX_SYMPTOMS -> symptoms + code
                                        else -> symptoms
                                    }
                                }
                            )
                        }
                    }
                    item {
                        EditorOptionSection(title = "心情", hint = "不做判断，只留下当天状态") {
                            ChoiceChipRow(
                                values = listOf(NO_ENTRY) + MenstruationFields.MOOD_VALUES,
                                selected = mood ?: NO_ENTRY,
                                label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.moodLabel(it) },
                                onSelect = { mood = it.takeIf { selected -> selected != NO_ENTRY } }
                            )
                        }
                    }
                    item {
                        EditorOptionSection(title = "分泌物", hint = "可留空") {
                            ChoiceChipRow(
                                values = listOf(NO_ENTRY) + MenstruationFields.DISCHARGE_VALUES,
                                selected = discharge ?: NO_ENTRY,
                                label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.dischargeLabel(it) },
                                onSelect = { discharge = it.takeIf { selected -> selected != NO_ENTRY } }
                            )
                        }
                    }
                    item {
                        EditorNoteCard(note = note, onNoteChange = { note = it })
                    }
                }
                EditorSaveBar(
                    filledCount = filledCount,
                    onSave = {
                        val localDateTime = LocalDateTime.of(
                            selectedDate,
                            LocalTime.of(selectedHour, selectedMinute)
                        )
                        val fields = buildMap {
                            flow?.let { put(MenstruationFields.FLOW, it) }
                            pain?.let { put(MenstruationFields.PAIN, it) }
                            if (symptoms.isNotEmpty()) {
                                put(MenstruationFields.SYMPTOMS, MenstruationFields.encodeSymptoms(symptoms))
                            }
                            mood?.let { put(MenstruationFields.MOOD, it) }
                            discharge?.let { put(MenstruationFields.DISCHARGE, it) }
                        }
                        onSave(
                            RecordDraft(
                                typeCode = MenstruationFields.TYPE_CODE,
                                occurredAtEpochMillis = localDateTime.atZone(recordZone).toInstant().toEpochMilli(),
                                zoneId = recordZone.id,
                                note = note.trim(),
                                detail = if (fields.isEmpty()) {
                                    null
                                } else {
                                    RecordDetail.Generic(MenstruationFields.TYPE_CODE, fields)
                                }
                            )
                        )
                    }
                )
            }
        }
    }

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
        EditorTimePickerDialog(
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
private fun EditorTopBar(title: String, accent: Color, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Bloodtype,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun EditorHero(
    dateText: String,
    timeText: String,
    filledCount: Int,
    palette: PeriodPalette
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = palette.accentContainer,
        border = BorderStroke(1.dp, palette.accentBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.surface.copy(alpha = 0.74f),
                    border = BorderStroke(1.dp, palette.accentBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Bloodtype,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "今天的状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$timeText · 已填 $filledCount / 6",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
            }
        }
    }
}

@Composable
private fun EditorDateTimeCard(
    dateText: String,
    timeText: String,
    zoneId: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("记录时间", style = MaterialTheme.typography.titleSmall)
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorPickerTile(
                    label = "日期",
                    value = dateText,
                    icon = Icons.Filled.CalendarMonth,
                    onClick = onDateClick,
                    modifier = Modifier.fillMaxWidth()
                )
                EditorPickerTile(
                    label = "时间",
                    value = timeText,
                    icon = Icons.Filled.Schedule,
                    onClick = onTimeClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorPickerTile(
                    label = "日期",
                    value = dateText,
                    icon = Icons.Filled.CalendarMonth,
                    onClick = onDateClick,
                    modifier = Modifier.weight(1f)
                )
                EditorPickerTile(
                    label = "时间",
                    value = timeText,
                    icon = Icons.Filled.Schedule,
                    onClick = onTimeClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = "按记录所在地时区保存：$zoneId",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorPickerTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun EditorOptionSection(
    title: String,
    hint: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun EditorNoteCard(note: String, onNoteChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("日记", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("今天想记下什么") },
            placeholder = { Text("记录身体感受、作息或想说的话") },
            supportingText = {
                Text("${note.length} 字 · 请勿记录他人身份信息；建议开启应用锁。")
            }
        )
        Text(
            text = "流量、痛经、症状、心情和分泌物都可以留空；选择后保存为同一条记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorSaveBar(filledCount: Int, onSave: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp
    ) {
        val stacked = LocalDensity.current.fontScale > 1.3f
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorSaveSummary(filledCount)
                EditorSaveButton(onSave, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorSaveSummary(filledCount, Modifier.weight(1f))
                EditorSaveButton(onSave)
            }
        }
    }
}

@Composable
private fun EditorSaveSummary(filledCount: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "保存这一天",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "已填 $filledCount / 6 · 仅保存在本机",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorSaveButton(onSave: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onSave,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text("保存记录", fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EditorTimePickerDialog(
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
                Spacer(Modifier.size(20.dp))
                TimePicker(state = state)
                Spacer(Modifier.size(12.dp))
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
