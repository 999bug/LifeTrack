@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.lifetrack.feature.water

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.domain.model.RecordDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val waterDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private fun waterQuickAmounts(unit: String): List<Int> =
    if (unit == "cup") listOf(1, 2, 3) else listOf(150, 200, 300, 500)

private data class WaterColors(
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val onAccent: Color,
    val hero: Color,
    val heroAlt: Color,
    val onHero: Color
)

/**
 * 饮水编辑器：全屏摘要式录入。
 *
 * 字段仍只有 amountMl 与 unit，不引入温度、口感等新业务字段；日期、时间和备注
 * 沿用原通用编辑器的保存口径。
 */
@Composable
fun WaterEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val recordZone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initialDateTime = remember(record) {
        Instant.ofEpochMilli(record?.event?.occurredAtEpochMillis ?: System.currentTimeMillis())
            .atZone(recordZone)
            .toLocalDateTime()
    }

    var amount by remember(record) { mutableStateOf(initial[WaterFields.AMOUNT].orEmpty()) }
    var unit by remember(record) {
        mutableStateOf(initial[WaterFields.UNIT] ?: WaterFields.UNITS.first())
    }
    var selectedDate by remember(record) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember(record) { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by remember(record) { mutableIntStateOf(initialDateTime.minute) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showUnitHelp by remember { mutableStateOf(false) }

    val valid = WaterFields.isValidAmount(amount)
    val unitLabel = WaterFields.unitLabel(unit)
    val timeText = String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)
    val bigFont = LocalDensity.current.fontScale > 1.3f

    fun adjustAmount(delta: Int) {
        val current = amount.toIntOrNull() ?: 0
        amount = (current + delta).coerceIn(
            WaterFields.AMOUNT_RANGE.first,
            WaterFields.AMOUNT_RANGE.last
        ).toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val colors = waterColors()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                WaterEditorTopBar(
                    title = if (record == null) "记录饮水" else "编辑饮水",
                    dateText = compactDateLabel(selectedDate, LocalDate.now()),
                    timeText = timeText,
                    bigFont = bigFont,
                    onClose = onDismiss,
                    onDateClick = { showDatePicker = true },
                    onTimeClick = { showTimePicker = true }
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        WaterHeroSummary(
                            amount = amount.ifBlank { "—" },
                            unit = unitLabel,
                            colors = colors,
                            bigFont = bigFont
                        )
                    }
                    item {
                        WaterQuickSection(
                            selectedAmount = amount,
                            unit = unit,
                            colors = colors,
                            onSelect = { amount = it.toString() }
                        )
                    }
                    item {
                        WaterAmountCard(
                            amount = amount,
                            unit = unit,
                            valid = valid,
                            colors = colors,
                            bigFont = bigFont,
                            onAmountChange = { amount = it.filter(Char::isDigit).take(4) },
                            onDecrease = { adjustAmount(if (unit == "cup") -1 else -50) },
                            onIncrease = { adjustAmount(if (unit == "cup") 1 else 50) },
                            onUnitSelect = { unit = it }
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { showUnitHelp = !showUnitHelp }) {
                                Text(if (showUnitHelp) "收起单位说明" else "单位说明")
                            }
                            AnimatedVisibility(visible = showUnitHelp) {
                                Text(
                                    "单位只用于显示，不做自动换算；同一条记录在整个应用与备份里使用同一个数值。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WaterSectionHeading(kicker = "可选", title = "留一句备注")
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("备注") },
                                placeholder = { Text("比如：运动后补水") },
                                minLines = 2,
                                maxLines = 5
                            )
                        }
                    }
                }
                WaterSaveBar(
                    amount = amount,
                    unit = unitLabel,
                    saveEnabled = valid,
                    bigFont = bigFont,
                    onSave = {
                        val localDateTime = LocalDateTime.of(
                            selectedDate,
                            LocalTime.of(selectedHour, selectedMinute)
                        )
                        onSave(
                            RecordDraft(
                                typeCode = WaterFields.TYPE_CODE,
                                occurredAtEpochMillis = localDateTime.atZone(recordZone).toInstant().toEpochMilli(),
                                zoneId = recordZone.id,
                                note = note.trim(),
                                detail = RecordDetail.Generic(
                                    WaterFields.TYPE_CODE,
                                    mapOf(
                                        WaterFields.AMOUNT to amount,
                                        WaterFields.UNIT to unit
                                    )
                                )
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
        WaterTimePickerDialog(
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
private fun waterColors(): WaterColors {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        WaterColors(
            accent = Color(0xFF8FC0D8),
            accentContainer = Color(0xFF1D313A),
            onAccentContainer = Color(0xFFC8E7F2),
            onAccent = Color(0xFF0E1510),
            hero = Color(0xFF102832),
            heroAlt = Color(0xFF1C3D49),
            onHero = Color(0xFFF4EBDA)
        )
    } else {
        WaterColors(
            accent = Color(0xFF3D7391),
            accentContainer = Color(0xFFE4EFF5),
            onAccentContainer = Color(0xFF21485B),
            onAccent = Color.White,
            hero = Color(0xFF173947),
            heroAlt = Color(0xFF285265),
            onHero = Color(0xFFF7F1E6)
        )
    }
}

@Composable
private fun WaterEditorTopBar(
    title: String,
    dateText: String,
    timeText: String,
    bigFont: Boolean,
    onClose: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!bigFont) {
                WaterMetaPill(dateText, Modifier, onDateClick)
                WaterMetaPill(timeText, Modifier, onTimeClick)
            }
        }
        if (bigFont) {
            WaterMetaPill(dateText, Modifier.fillMaxWidth(), onDateClick)
            WaterMetaPill(timeText, Modifier.fillMaxWidth(), onTimeClick)
        }
    }
}

@Composable
private fun WaterMetaPill(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WaterHeroSummary(
    amount: String,
    unit: String,
    colors: WaterColors,
    bigFont: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.hero,
        contentColor = colors.onHero
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TODAY'S WATER",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onHero.copy(alpha = 0.64f),
                    letterSpacing = 1.2.sp
                )
                Text(
                    "本杯摘要",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onHero.copy(alpha = 0.58f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, colors.onHero.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = colors.onHero
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "HYDRATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = "$amount $unit",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "按下保存前，再确认一次份量",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onHero.copy(alpha = 0.72f)
                    )
                }
            }
            HorizontalDivider(color = colors.onHero.copy(alpha = 0.16f))
            if (bigFont) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WaterHeroStat("本次", "$amount $unit", colors)
                    WaterHeroStat("单位", waterUnitDescription(unit), colors)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    WaterHeroStat("本次", "$amount $unit", colors, Modifier.weight(1f))
                    WaterHeroStat("单位", waterUnitDescription(unit), colors, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WaterHeroStat(
    label: String,
    value: String,
    colors: WaterColors,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onHero.copy(alpha = 0.55f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WaterQuickSection(
    selectedAmount: String,
    unit: String,
    colors: WaterColors,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WaterSectionHeading(kicker = "QUICK POUR", title = "快捷份量")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            waterQuickAmounts(unit).forEach { value ->
                FilterChip(
                    selected = selectedAmount == value.toString(),
                    onClick = { onSelect(value) },
                    label = { Text("$value ${WaterFields.unitLabel(unit)}") },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = colors.accent,
                        selectedLabelColor = colors.onAccent
                    )
                )
            }
        }
    }
}

@Composable
private fun WaterAmountCard(
    amount: String,
    unit: String,
    valid: Boolean,
    colors: WaterColors,
    bigFont: Boolean,
    onAmountChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onUnitSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bigFont) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("份量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${amount.ifBlank { "—" }} ${WaterFields.unitLabel(unit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accent
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("份量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${amount.ifBlank { "—" }} ${WaterFields.unitLabel(unit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accent
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WaterStepperButton("减少份量", false, colors, onDecrease)
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("饮用量") },
                    singleLine = true,
                    isError = amount.isNotEmpty() && !valid,
                    supportingText = if (amount.isNotEmpty() && !valid) {
                        {
                            Text(
                                "请输入 ${WaterFields.AMOUNT_RANGE.first}–${WaterFields.AMOUNT_RANGE.last} 的整数"
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                WaterStepperButton("增加份量", true, colors, onIncrease)
            }
            Text("单位", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WaterFields.UNITS.forEach { value ->
                    FilterChip(
                        selected = unit == value,
                        onClick = { onUnitSelect(value) },
                        label = { Text(WaterFields.unitLabel(value)) },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = colors.onAccent
                        )
                    )
                }
            }
            Text(
                "快捷份量按当前单位写入，不自动换算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WaterStepperButton(
    description: String,
    increase: Boolean,
    colors: WaterColors,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.accentContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (increase) Icons.Filled.Add else Icons.Filled.Remove,
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = colors.onAccentContainer
            )
        }
    }
}

@Composable
private fun WaterSectionHeading(kicker: String, title: String) {
    Column {
        Text(
            text = kicker,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WaterSaveBar(
    amount: String,
    unit: String,
    saveEnabled: Boolean,
    bigFont: Boolean,
    onSave: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp
    ) {
        if (bigFont) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WaterSaveSummary(amount, unit)
                WaterSaveButton(saveEnabled, onSave, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WaterSaveSummary(amount, unit, Modifier.weight(1f))
                WaterSaveButton(saveEnabled, onSave)
            }
        }
    }
}

@Composable
private fun WaterSaveSummary(amount: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "准备保存",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "饮水 · ${amount.ifBlank { "—" }} $unit",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WaterSaveButton(
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
        Text("保存饮水", fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun WaterTimePickerDialog(
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

private fun compactDateLabel(date: LocalDate, today: LocalDate): String =
    if (date == today) "今天" else date.format(waterDateFormatter)

private fun waterUnitDescription(label: String): String =
    if (label == "杯") "按杯数记录" else "按毫升记录"
