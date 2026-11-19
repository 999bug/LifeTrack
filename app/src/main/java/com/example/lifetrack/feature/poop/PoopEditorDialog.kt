@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.lifetrack.feature.poop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.example.lifetrack.domain.model.PoopRecord
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.domain.model.PoopRecordDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val poopDateFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)
private val poopTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

/**
 * 排便编辑器：暖棕全屏分组表单。
 *
 * 对外签名保持不变。发生时间、用时、形态与感受全部沿用原字段；保存仍由
 * [PoopFields.resolve] 产出，未新增排便量、费力程度或任何其他业务字段。
 */
@Composable
fun PoopEditorDialog(
    record: PoopRecord?,
    onDismiss: () -> Unit,
    onSave: (PoopRecordDraft) -> Unit
) {
    val accent = LifeTrackTokens.ModuleColors.of(PoopFields.TYPE_CODE)
    val zone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initial = remember(record) {
        Instant.ofEpochMilli(record?.event?.occurredAtEpochMillis ?: System.currentTimeMillis())
            .atZone(zone)
            .toLocalDateTime()
    }
    var selectedDate by remember(record) { mutableStateOf(initial.toLocalDate()) }
    var selectedHour by remember(record) { mutableIntStateOf(initial.hour) }
    var selectedMinute by remember(record) { mutableIntStateOf(initial.minute) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }
    var bristol by remember(record) { mutableStateOf(record?.detail?.bristolType) }
    var amount by remember(record) { mutableStateOf(record?.detail?.amountCode) }
    var effort by remember(record) { mutableStateOf(record?.detail?.effortCode) }
    var urgency by remember(record) { mutableStateOf(record?.detail?.urgency) }
    var abdominal by remember(record) { mutableStateOf(record?.detail?.abdominalDiscomfort) }
    var incomplete by remember(record) { mutableStateOf(record?.detail?.incompleteEvacuation) }
    var showGuide by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val startMillis = record?.event?.occurredAtEpochMillis
    var endedAt by remember(record) { mutableStateOf(record?.detail?.endedAtEpochMillis) }
    var manual by remember(record) {
        val fromManual = record?.detail?.endedAtEpochMillis == null
        mutableStateOf(
            if (fromManual) record?.detail?.durationMinutes?.toString().orEmpty() else ""
        )
    }
    val nowMillis = remember(startMillis) { System.currentTimeMillis() }
    val elapsed = if (startMillis == null) 0 else PoopFields.measuredMinutes(startMillis, nowMillis)
    val measured = if (startMillis == null || endedAt == null) {
        null
    } else {
        PoopFields.measuredMinutes(startMillis, endedAt!!)
    }
    val manualValid = manual.isEmpty() || PoopFields.isValidManualDuration(manual)
    val measuredValid = measured != null && PoopFields.isMeasuredValid(measured)
    val largeFont = LocalDensity.current.fontScale > 1.3f

    val selectedTime = String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)
    val heroTitle = if (record == null) "记录这一次" else "这次排便"
    val heroMeta = buildList {
        bristol?.let { add("布里斯托 $it 型") }
        measured?.takeIf { measuredValid }?.let { add("用时 $it 分钟") }
        if (manual.isNotBlank() && manualValid) add("用时 $manual 分钟")
        if (isEmpty()) add("可以留空，只记时间")
    }.joinToString(" · ")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PoopEditorHeader(
                    isEditing = record != null,
                    accent = accent,
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = LifeTrackTokens.Spacing.page),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PoopHeroCard(
                        title = heroTitle,
                        timeLabel = "${selectedDate.format(poopDateFormatter)} · $selectedTime",
                        meta = heroMeta,
                        accent = accent
                    )

                    PoopSectionCard(
                        number = "01",
                        title = "发生时间",
                        description = "日期与时间决定这条记录在时间轴上的位置。",
                        accent = accent
                    ) {
                        if (largeFont) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PoopPickerCard(
                                    label = "日期",
                                    value = selectedDate.format(poopDateFormatter),
                                    accent = accent,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showDatePicker = true }
                                )
                                PoopPickerCard(
                                    label = "时间",
                                    value = selectedTime,
                                    accent = accent,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showTimePicker = true }
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PoopPickerCard(
                                    label = "日期",
                                    value = selectedDate.format(poopDateFormatter),
                                    accent = accent,
                                    modifier = Modifier.weight(1f),
                                    onClick = { showDatePicker = true }
                                )
                                PoopPickerCard(
                                    label = "时间",
                                    value = selectedTime,
                                    accent = accent,
                                    modifier = Modifier.weight(1f),
                                    onClick = { showTimePicker = true }
                                )
                            }
                        }
                        Text(
                            text = "按记录所在地时区保存：${zone.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PoopSectionCard(
                        number = "02",
                        title = "用时",
                        description = "可选。结束后自动计算，也可以直接手动填写。",
                        accent = accent
                    ) {
                        PoopTimingSection(
                            hasStart = startMillis != null,
                            startLabel = startMillis?.let { formatTime(it, zone) },
                            endLabel = endedAt?.let { formatTime(it, zone) },
                            elapsed = elapsed,
                            measured = measured,
                            measuredValid = measuredValid,
                            manual = manual,
                            manualValid = manualValid,
                            accent = accent,
                            onEnd = {
                                endedAt = System.currentTimeMillis()
                                manual = ""
                            },
                            onReTime = { endedAt = null },
                            onManualChange = { input ->
                                manual = input.filter(Char::isDigit).take(3)
                                if (manual.isNotEmpty()) endedAt = null
                            }
                        )
                    }

                    PoopSectionCard(
                        number = "03",
                        title = "形态与感受",
                        description = "只需要选择接近本次情况的项目，全部可以留空。",
                        accent = accent
                    ) {
                        BristolTypePicker(
                            selected = bristol,
                            onSelect = { bristol = it },
                            onShowGuide = { showGuide = true }
                        )
                        PoopChoiceField(
                            label = "排便量",
                            labels = listOf("少量", "适中", "大量"),
                            values = listOf(1, 2, 3),
                            selected = amount,
                            accent = accent,
                            onSelect = { amount = it }
                        )
                        PoopChoiceField(
                            label = "费力程度",
                            labels = listOf("不费力", "轻微", "明显"),
                            values = listOf(1, 2, 3),
                            selected = effort,
                            accent = accent,
                            onSelect = { effort = it }
                        )
                        PoopTriStateField("便意急迫感", urgency, accent) { urgency = it }
                        PoopTriStateField("腹部不适", abdominal, accent) { abdominal = it }
                        PoopTriStateField("排不净感", incomplete, accent) { incomplete = it }
                    }

                    PoopSectionCard(
                        number = "04",
                        title = "备注",
                        description = "可以记录饮食、作息或身体感觉，不要写入他人身份信息。",
                        accent = accent
                    ) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("这一笔想补充什么？") },
                            minLines = 3,
                            supportingText = {
                                Text("只保存在本机；留空也可以保存。")
                            }
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                }

                PoopSaveBar(
                    timeLabel = "$selectedTime · $heroMeta",
                    isEditing = record != null,
                    enabled = manualValid,
                    onSave = {
                        val localDateTime = LocalDateTime.of(
                            selectedDate,
                            LocalTime.of(selectedHour, selectedMinute)
                        )
                        val millis = localDateTime.atZone(zone).toInstant().toEpochMilli()
                        val timing = PoopFields.resolve(
                            startEpochMillis = millis,
                            endedAtEpochMillis = endedAt,
                            manualMinutes = manual
                        )
                        onSave(
                            PoopRecordDraft(
                                occurredAtEpochMillis = millis,
                                zoneId = zone.id,
                                bristolType = bristol,
                                amountCode = amount,
                                effortCode = effort,
                                urgency = urgency,
                                abdominalDiscomfort = abdominal,
                                incompleteEvacuation = incomplete,
                                note = note.trim(),
                                endedAtEpochMillis = timing.endedAtEpochMillis,
                                durationMinutes = timing.durationMinutes
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
        PoopTimePickerDialog(
            state = state,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                selectedHour = state.hour
                selectedMinute = state.minute
                showTimePicker = false
            }
        )
    }

    if (showGuide) {
        BristolGuideDialog(onDismiss = { showGuide = false })
    }
}

@Composable
private fun PoopEditorHeader(
    isEditing: Boolean,
    accent: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                text = if (isEditing) "编辑排便记录" else "排便记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "身体观察 · 暖棕记录",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("本地保存", style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}

@Composable
private fun PoopHeroCard(
    title: String,
    timeLabel: String,
    meta: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Loop, contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(timeLabel, style = MaterialTheme.typography.bodyMedium, color = accent)
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PoopSectionCard(
    number: String,
    title: String,
    description: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun PoopPickerCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PoopChoiceField(
    label: String,
    labels: List<String>,
    values: List<Int>,
    selected: Int?,
    accent: Color,
    onSelect: (Int?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PoopFilterChip(
                label = "未记录",
                selected = selected == null,
                accent = accent,
                onClick = { onSelect(null) }
            )
            labels.zip(values).forEach { (text, value) ->
                PoopFilterChip(
                    label = text,
                    selected = selected == value,
                    accent = accent,
                    onClick = { onSelect(if (selected == value) null else value) }
                )
            }
        }
    }
}

@Composable
private fun PoopTriStateField(
    label: String,
    value: Boolean?,
    accent: Color,
    onChange: (Boolean?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PoopFilterChip("未记录", value == null, accent) { onChange(null) }
            PoopFilterChip("否", value == false, accent) { onChange(false) }
            PoopFilterChip("是", value == true, accent) { onChange(true) }
        }
    }
}

@Composable
private fun PoopFilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.15f),
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent
        )
    )
}

@Composable
private fun PoopTimingSection(
    hasStart: Boolean,
    startLabel: String?,
    endLabel: String?,
    elapsed: Int,
    measured: Int?,
    measuredValid: Boolean,
    manual: String,
    manualValid: Boolean,
    accent: Color,
    onEnd: () -> Unit,
    onReTime: () -> Unit,
    onManualChange: (String) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        when {
            !hasStart -> Text(
                text = "保存后开始计时：这次只记开始时间，再次进入这条记录时可以记录结束时间，自动算出用时。",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )

            measured == null && manual.isEmpty() && elapsed > PoopFields.MAX_DURATION_MINUTES -> Text(
                text = "这次记录开始于 $startLabel，已超过 ${PoopFields.MAX_DURATION_MINUTES} 分钟，" +
                    "自动计时不再有效；如果实际用时很短，请在下面手动填写。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            measured == null && manual.isEmpty() -> {
                Text(
                    text = "进行中 · 开始于 $startLabel。结束时点下面的按钮，把现在记成结束时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                OutlinedButton(onClick = onEnd) { Text("结束记录（现在）") }
            }

            measured != null && !measuredValid -> {
                Text(
                    text = "结束时间距开始已 $measured 分钟，超过 ${PoopFields.MAX_DURATION_MINUTES} 分钟，" +
                        "本次用时判为无效（记录本身会保留）；可手动填写实际用时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onReTime) { Text("清除结束时间") }
            }

            measured != null -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "已用时 $measured 分钟 · 结束于 $endLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReTime) { Text("重新计时") }
            }
        }

        OutlinedTextField(
            value = manual,
            onValueChange = onManualChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手动填写用时（分钟）") },
            supportingText = {
                Text(
                    "留空表示不手动填写；可填 ${PoopFields.DURATION_RANGE.first}–" +
                        "${PoopFields.DURATION_RANGE.last} 分钟"
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !manualValid
        )
        if (!manualValid) {
            Text(
                text = "请输入 ${PoopFields.DURATION_RANGE.first}–${PoopFields.DURATION_RANGE.last} 之间的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (manual.isNotEmpty()) {
            Text(
                text = "已切换为手动用时，结束时间不会写入。",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

@Composable
private fun PoopSaveBar(
    timeLabel: String,
    isEditing: Boolean,
    enabled: Boolean,
    onSave: () -> Unit
) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp
    ) {
        if (largeFont) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                PoopSaveButton(isEditing, enabled, onSave, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                PoopSaveButton(isEditing, enabled, onSave)
            }
        }
    }
}

@Composable
private fun PoopSaveButton(
    isEditing: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onSave,
        modifier = modifier.heightIn(min = 50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (isEditing) "保存修改" else "保存记录",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PoopTimePickerDialog(
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

private fun formatTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(poopTimeFormatter)
