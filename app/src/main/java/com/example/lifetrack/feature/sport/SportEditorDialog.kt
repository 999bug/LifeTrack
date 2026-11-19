@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.lifetrack.feature.sport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.domain.model.RecordDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 时长快捷预设：覆盖最常见的单次时长，点一下填入，仍可手改。 */
private val DURATION_PRESETS = listOf(15, 30, 45, 60, 90)
private val editorDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
/**
 * 运动编辑器：全屏录入页，仅承载类型 / 时长 / 强度三个既有详情字段。
 *
 * 打开旧类型记录时仍不预选任何类型，并要求显式重选后才允许保存；直接关闭不会改值。
 * 日期、时区、备注沿用事件层原有语义，不新增数据库字段。
 */
@Composable
fun SportEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit,
    initialSport: String? = null
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val storedSport = initial[SportFields.SPORT]?.takeIf { it.isNotEmpty() }
    val recordZone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initialDateTime = remember(record) {
        Instant.ofEpochMilli(record?.event?.occurredAtEpochMillis ?: System.currentTimeMillis())
            .atZone(recordZone)
            .toLocalDateTime()
    }

    var sport by remember(record, initialSport) {
        mutableStateOf(
            when {
                storedSport != null && SportFields.isSelectable(storedSport) -> storedSport
                storedSport != null -> ""
                initialSport != null && SportFields.isSelectable(initialSport) -> initialSport
                else -> ""
            }
        )
    }
    var duration by remember(record) { mutableStateOf(initial[SportFields.DURATION].orEmpty()) }
    var intensity by remember(record) {
        mutableStateOf(initial[SportFields.INTENSITY] ?: SportFields.INTENSITIES[1])
    }
    var selectedDate by remember(record) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember(record) { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by remember(record) { mutableIntStateOf(initialDateTime.minute) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val legacySport = storedSport?.takeIf { SportFields.isLegacy(it) }
    val durationValid = SportFields.isValidDuration(duration)
    val saveEnabled = durationValid && sport.isNotEmpty()
    val palette = sportPalette()
    val stacked = LocalDensity.current.fontScale > 1.3f
    val dateText = compactEditorDateLabel(selectedDate, LocalDate.now())
    val timeText = String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)

    fun submit() {
        if (!saveEnabled) return
        val localDateTime = LocalDateTime.of(selectedDate, LocalTime.of(selectedHour, selectedMinute))
        onSave(
            RecordDraft(
                typeCode = SportFields.TYPE_CODE,
                occurredAtEpochMillis = localDateTime.atZone(recordZone).toInstant().toEpochMilli(),
                zoneId = recordZone.id,
                note = note.trim(),
                detail = RecordDetail.Generic(
                    SportFields.TYPE_CODE,
                    mapOf(
                        SportFields.SPORT to sport,
                        SportFields.DURATION to duration,
                        SportFields.INTENSITY to intensity
                    )
                )
            )
        )
    }

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
                SportEditorTopBar(
                    title = if (record == null) "记录运动" else "编辑运动",
                    dateText = dateText,
                    timeText = timeText,
                    onClose = onDismiss,
                    onDateClick = { showDatePicker = true },
                    onTimeClick = { showTimePicker = true }
                )

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    item {
                        SportEditorHero(
                            sport = sport,
                            duration = duration,
                            intensity = intensity,
                            palette = palette
                        )
                    }
                    item {
                        SportTypeSection(
                            selected = sport,
                            legacySport = legacySport,
                            stacked = stacked,
                            palette = palette,
                            onSelect = { sport = it }
                        )
                    }
                    item {
                        SportDurationSection(
                            duration = duration,
                            valid = durationValid,
                            stacked = stacked,
                            palette = palette,
                            onDurationChange = { duration = it },
                            onPreset = { duration = it }
                        )
                    }
                    item {
                        SportIntensitySection(
                            selected = intensity,
                            stacked = stacked,
                            palette = palette,
                            onSelect = { intensity = it }
                        )
                    }
                    item {
                        SportTimeNoteSection(
                            dateText = dateText,
                            timeText = timeText,
                            zoneId = recordZone.id,
                            note = note,
                            onDateClick = { showDatePicker = true },
                            onTimeClick = { showTimePicker = true },
                            onNoteChange = { note = it }
                        )
                    }
                }

                SportEditorSaveBar(
                    sport = sport,
                    duration = duration,
                    intensity = intensity,
                    durationValid = durationValid,
                    stacked = stacked,
                    onSave = { submit() }
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = PickerDateMapper.toUtcMillis(selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = PickerDateMapper.fromUtcMillis(it)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        SportTimePickerDialog(
            state = timePickerState,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                selectedHour = timePickerState.hour
                selectedMinute = timePickerState.minute
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun SportEditorTopBar(
    title: String,
    dateText: String,
    timeText: String,
    onClose: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "关闭运动编辑器")
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "类型 · 时长 · 强度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onDateClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(dateText)
            }
            OutlinedButton(
                onClick = onTimeClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(timeText)
            }
        }
    }
}

@Composable
private fun SportEditorHero(
    sport: String,
    duration: String,
    intensity: String,
    palette: SportPalette
) {
    val sportName = if (sport.isBlank()) "先选一种运动" else SportFields.sportLabel(sport)
    val durationText = if (SportFields.isValidDuration(duration)) {
        SportFields.minutesLabel(duration.toInt())
    } else {
        "时长待填写"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .background(
                Brush.linearGradient(listOf(palette.hero, palette.heroElevated)),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, palette.accent.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = palette.accent.copy(alpha = 0.08f),
                radius = size.minDimension * 0.50f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.15f)
            )
            drawCircle(
                color = palette.onHero.copy(alpha = 0.06f),
                radius = size.minDimension * 0.34f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.82f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "TODAY'S MOVEMENT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
            Text(
                text = sportName,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onHero.copy(alpha = 0.78f)
            )
            Text(
                text = durationText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.onHero,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = SportFields.intensityLabel(intensity),
                style = MaterialTheme.typography.labelLarge,
                color = palette.onHero.copy(alpha = 0.74f)
            )
        }
    }
}

@Composable
private fun SportTypeSection(
    selected: String,
    legacySport: String?,
    stacked: Boolean,
    palette: SportPalette,
    onSelect: (String) -> Unit
) {
    SportEditorSection(
        title = "运动类型",
        hint = "只保留瑜伽 / 骑行 / 跑步",
        required = true
    ) {
        if (legacySport != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    text = "这条记录的类型是「${SportFields.sportLabel(legacySport)}」，该类型已从界面下线。" +
                        "请重新选一个类型再保存；直接关闭不会改动原值。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SportFields.SPORTS.forEach { sport ->
                    SportTypeCard(
                        sport = sport,
                        selected = selected == sport,
                        stacked = true,
                        palette = palette,
                        onClick = { onSelect(sport) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportFields.SPORTS.forEach { sport ->
                    SportTypeCard(
                        sport = sport,
                        selected = selected == sport,
                        stacked = false,
                        palette = palette,
                        onClick = { onSelect(sport) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (selected.isBlank()) {
            Text(
                text = "选择类型后才能保存；旧记录不会被静默改类型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SportTypeCard(
    sport: String,
    selected: Boolean,
    stacked: Boolean,
    palette: SportPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = sportTypeColor(sport, palette.accent, MaterialTheme.colorScheme.surface)
    val content: @Composable () -> Unit = {
        if (stacked) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SportEditorIcon(sport = sport, accent = accent, selected = selected)
                Text(
                    text = SportFields.sportLabel(sport),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    SportEditorIcon(sport = sport, accent = accent, selected = selected, size = 36, iconSize = 20)
                    Spacer(Modifier.weight(1f))
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = SportFields.sportLabel(sport),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Card(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
            contentDescription = SportFields.sportLabel(sport)
        },
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) palette.accentContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        content()
    }
}

@Composable
private fun SportEditorIcon(
    sport: String,
    accent: Color,
    selected: Boolean,
    size: Int = 42,
    iconSize: Int = 22
) {
    val palette = sportPalette()
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                if (selected) palette.accent else accent.copy(alpha = 0.12f),
                RoundedCornerShape((size * 0.32f).dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = sportIcon(sport),
            contentDescription = null,
            tint = if (selected) palette.onAccent else accent,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

@Composable
private fun SportDurationSection(
    duration: String,
    valid: Boolean,
    stacked: Boolean,
    palette: SportPalette,
    onDurationChange: (String) -> Unit,
    onPreset: (String) -> Unit
) {
    val numeric = duration.toIntOrNull()
    val sliderValue = (numeric ?: 30).coerceIn(1, 240).toFloat()
    fun adjust(delta: Int) {
        val next = ((numeric ?: 0) + delta).coerceIn(
            SportFields.DURATION_RANGE.first,
            SportFields.DURATION_RANGE.last
        )
        onDurationChange(next.toString())
    }

    SportEditorSection(
        title = "运动时长",
        hint = "${SportFields.DURATION_RANGE.first}-${SportFields.DURATION_RANGE.last} 分钟",
        required = true
    ) {
        if (stacked) {
            DurationTextField(
                duration = duration,
                valid = valid,
                modifier = Modifier.fillMaxWidth(),
                onDurationChange = onDurationChange
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DurationStepButton(
                    icon = Icons.Filled.Remove,
                    description = "减少五分钟",
                    onClick = { adjust(-5) },
                    modifier = Modifier.weight(1f)
                )
                DurationStepButton(
                    icon = Icons.Filled.Add,
                    description = "增加五分钟",
                    onClick = { adjust(5) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DurationStepButton(
                    icon = Icons.Filled.Remove,
                    description = "减少五分钟",
                    onClick = { adjust(-5) }
                )
                DurationTextField(
                    duration = duration,
                    valid = valid,
                    modifier = Modifier.weight(1f),
                    onDurationChange = onDurationChange
                )
                DurationStepButton(
                    icon = Icons.Filled.Add,
                    description = "增加五分钟",
                    onClick = { adjust(5) }
                )
            }
        }
        if (duration.isNotEmpty() && !valid) {
            Text(
                text = "请输入 ${SportFields.DURATION_RANGE.first}-${SportFields.DURATION_RANGE.last} 之间的整数分钟。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { onDurationChange(it.roundToInt().toString()) },
            valueRange = 1f..240f,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "运动时长快捷滑杆，1 到 240 分钟"
            }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("240 分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DURATION_PRESETS.forEach { preset ->
                FilterChip(
                    selected = numeric == preset,
                    onClick = { onPreset(preset.toString()) },
                    label = { Text("$preset 分钟") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accentContainer,
                        selectedLabelColor = palette.accentStrong
                    )
                )
            }
        }
        Text(
            text = "滑杆快捷范围 1-240 分钟；更长的时长可直接输入，最多 ${SportFields.DURATION_RANGE.last} 分钟。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DurationTextField(
    duration: String,
    valid: Boolean,
    modifier: Modifier,
    onDurationChange: (String) -> Unit
) {
    OutlinedTextField(
        value = duration,
        onValueChange = { onDurationChange(it.filter(Char::isDigit).take(4)) },
        modifier = modifier,
        label = { Text("分钟") },
        placeholder = { Text("例如 35") },
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = duration.isNotEmpty() && !valid
    )
}

@Composable
private fun DurationStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SportIntensitySection(
    selected: String,
    stacked: Boolean,
    palette: SportPalette,
    onSelect: (String) -> Unit
) {
    SportEditorSection(
        title = "运动强度",
        hint = "选一个最接近体感的",
        required = true
    ) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SportFields.INTENSITIES.forEach { intensity ->
                    IntensityCard(
                        value = intensity,
                        selected = selected == intensity,
                        stacked = true,
                        palette = palette,
                        onClick = { onSelect(intensity) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportFields.INTENSITIES.forEach { intensity ->
                    IntensityCard(
                        value = intensity,
                        selected = selected == intensity,
                        stacked = false,
                        palette = palette,
                        onClick = { onSelect(intensity) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun IntensityCard(
    value: String,
    selected: Boolean,
    stacked: Boolean,
    palette: SportPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = when (value) {
        "low" -> "轻松 · 可以聊天"
        "high" -> "挑战 · 呼吸加快"
        else -> "舒适 · 微微出汗"
    }
    Card(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
            contentDescription = "${SportFields.intensityLabel(value)}，$description"
        },
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) palette.accentContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) palette.accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        if (stacked) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = SportFields.intensityLabel(value),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) palette.onAccentContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) palette.onAccentContainer.copy(alpha = 0.78f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = SportFields.intensityLabel(value),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) palette.onAccentContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) palette.onAccentContainer.copy(alpha = 0.78f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SportTimeNoteSection(
    dateText: String,
    timeText: String,
    zoneId: String,
    note: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNoteChange: (String) -> Unit
) {
    SportEditorSection(title = "时间与备注", hint = "默认记录此刻") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onDateClick) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(dateText)
            }
            OutlinedButton(onClick = onTimeClick) {
                Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(timeText)
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注（可选）") },
            placeholder = { Text("比如：晚饭后沿河慢跑") },
            minLines = 2,
            maxLines = 4
        )
        Text(
            text = "按记录所在地时区保存：$zoneId",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SportEditorSection(
    title: String,
    hint: String? = null,
    required: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (hint != null) {
                Text(
                    text = if (required) "必填 · $hint" else hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
        content()
    }
}

@Composable
private fun SportEditorSaveBar(
    sport: String,
    duration: String,
    intensity: String,
    durationValid: Boolean,
    stacked: Boolean,
    onSave: () -> Unit
) {
    val sportName = if (sport.isBlank()) "先选择运动类型" else SportFields.sportLabel(sport)
    val meta = if (durationValid) {
        "${SportFields.minutesLabel(duration.toInt())} · ${SportFields.intensityLabel(intensity)}"
    } else {
        "填写有效时长后保存"
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp
    ) {
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SaveSummary(sportName = sportName, meta = meta)
                SaveButton(
                    enabled = durationValid && sport.isNotBlank(),
                    onSave = onSave,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SaveSummary(sportName = sportName, meta = meta, modifier = Modifier.weight(1f))
                SaveButton(enabled = durationValid && sport.isNotBlank(), onSave = onSave)
            }
        }
    }
}

@Composable
private fun SaveSummary(sportName: String, meta: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = sportName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$meta · 保存后更新本周摘要",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
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
        Text("保存这次运动", fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SportTimePickerDialog(
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

private fun compactEditorDateLabel(date: LocalDate, today: LocalDate): String =
    if (date == today) "今天 · ${date.format(editorDateFormatter)}" else date.format(editorDateFormatter)