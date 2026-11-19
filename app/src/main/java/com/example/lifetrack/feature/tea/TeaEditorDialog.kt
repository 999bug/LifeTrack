@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.lifetrack.feature.tea

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
import androidx.compose.material.icons.filled.Spa
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
import com.example.lifetrack.core.model.TeaFields
import com.example.lifetrack.domain.model.RecordDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val teaDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val teaQuickPairs = listOf(
    TeaQuickPair(1, 300, "1 杯"),
    TeaQuickPair(2, 600, "2 杯"),
    TeaQuickPair(3, 900, "3 杯")
)

private data class TeaQuickPair(val cups: Int, val amount: Int, val label: String)

private data class TeaColors(
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val onAccent: Color,
    val hero: Color,
    val heroAlt: Color,
    val onHero: Color
)

/**
 * 茶编辑器：与饮水共享全屏摘要骨架，但使用低饱和茶绿和茶种选择。
 *
 * 只写入茶种、杯数、毫升与热量四个既有字段；日期、时间和备注继续走原有草稿口径。
 */
@Composable
fun TeaEditorDialog(
    record: BackupRecord?,
    customVarieties: List<String>,
    onAddVariety: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val initialVariety = initial[TeaFields.VARIETY] ?: TeaFields.PRESET_VARIETIES.first()
    val recordZone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initialDateTime = remember(record) {
        Instant.ofEpochMilli(record?.event?.occurredAtEpochMillis ?: System.currentTimeMillis())
            .atZone(recordZone)
            .toLocalDateTime()
    }

    var variety by remember(record) { mutableStateOf(initialVariety) }
    var cups by remember(record) { mutableStateOf(initial[TeaFields.CUPS] ?: "1") }
    var amount by remember(record) { mutableStateOf(initial[TeaFields.AMOUNT] ?: "300") }
    var calories by remember(record) {
        mutableStateOf(initial[TeaFields.CALORIES] ?: TeaFields.defaultCalories(initialVariety)?.toString().orEmpty())
    }
    var lastAutoCalories by remember(record) {
        mutableStateOf(TeaFields.defaultCalories(initialVariety)?.toString().orEmpty())
    }
    var selectedDate by remember(record) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember(record) { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by remember(record) { mutableIntStateOf(initialDateTime.minute) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }
    var addingVariety by remember(record) { mutableStateOf(false) }
    var newVariety by remember(record) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showFieldHelp by remember { mutableStateOf(false) }

    val allVarieties = TeaFields.PRESET_VARIETIES + customVarieties.filterNot { it in TeaFields.PRESET_VARIETIES }
    val cupsValid = TeaFields.isValidCups(cups)
    val amountValid = TeaFields.isValidAmount(amount)
    val caloriesValid = calories.isEmpty() || TeaFields.isValidCalories(calories)
    val saveEnabled = cupsValid && amountValid && caloriesValid && variety.isNotBlank()
    val timeText = String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)
    val bigFont = LocalDensity.current.fontScale > 1.3f

    fun selectVariety(value: String) {
        variety = value
        if (calories.isEmpty() || calories == lastAutoCalories) {
            val auto = TeaFields.defaultCalories(value)?.toString().orEmpty()
            calories = auto
            lastAutoCalories = auto
        }
    }

    fun submitVariety() {
        val name = newVariety.trim()
        if (name.isNotEmpty() && TeaFields.isValidVariety(name)) {
            onAddVariety(name)
            selectVariety(name)
            newVariety = ""
            addingVariety = false
        }
    }

    fun adjustCups(delta: Int) {
        val current = cups.toIntOrNull() ?: 1
        cups = (current + delta).coerceIn(
            TeaFields.CUPS_RANGE.first,
            TeaFields.CUPS_RANGE.last
        ).toString()
    }

    fun adjustAmount(delta: Int) {
        val current = amount.toIntOrNull() ?: 0
        amount = (current + delta).coerceIn(
            TeaFields.AMOUNT_RANGE.first,
            TeaFields.AMOUNT_RANGE.last
        ).toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val colors = teaColors()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                TeaEditorTopBar(
                    title = if (record == null) "记一杯茶" else "编辑茶记录",
                    dateText = compactTeaDateLabel(selectedDate, LocalDate.now()),
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
                        TeaHeroSummary(
                            variety = variety,
                            cups = cups,
                            amount = amount,
                            calories = calories,
                            colors = colors,
                            bigFont = bigFont
                        )
                    }
                    item {
                        TeaVarietySection(
                            varieties = allVarieties,
                            selected = variety,
                            adding = addingVariety,
                            newVariety = newVariety,
                            colors = colors,
                            bigFont = bigFont,
                            onSelect = ::selectVariety,
                            onToggleAdding = { addingVariety = !addingVariety },
                            onNewVarietyChange = { newVariety = it.take(TeaFields.MAX_VARIETY_LENGTH) },
                            onSubmit = ::submitVariety
                        )
                    }
                    item {
                        TeaQuickSection(
                            cups = cups,
                            amount = amount,
                            colors = colors,
                            onSelect = { pair ->
                                cups = pair.cups.toString()
                                amount = pair.amount.toString()
                            }
                        )
                    }
                    item {
                        TeaQuantityCard(
                            cups = cups,
                            amount = amount,
                            calories = calories,
                            cupsValid = cupsValid,
                            amountValid = amountValid,
                            caloriesValid = caloriesValid,
                            colors = colors,
                            bigFont = bigFont,
                            onCupsChange = { cups = it.filter(Char::isDigit).take(2) },
                            onAmountChange = { amount = it.filter(Char::isDigit).take(4) },
                            onCaloriesChange = { calories = it.filter(Char::isDigit).take(4) },
                            onDecreaseCups = { adjustCups(-1) },
                            onIncreaseCups = { adjustCups(1) },
                            onDecreaseAmount = { adjustAmount(-50) },
                            onIncreaseAmount = { adjustAmount(50) }
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { showFieldHelp = !showFieldHelp }) {
                                Text(if (showFieldHelp) "收起字段说明" else "字段说明")
                            }
                            AnimatedVisibility(visible = showFieldHelp) {
                                Text(
                                    "热量选填，单位为千卡；自定义茶种默认不估热量。茶种添加后会保留在本地。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TeaSectionHeading(kicker = "可选", title = "留一句备注")
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("备注") },
                                placeholder = { Text("比如：午后的一壶茶") },
                                minLines = 2,
                                maxLines = 5
                            )
                        }
                    }
                }
                TeaSaveBar(
                    variety = variety,
                    cups = cups,
                    amount = amount,
                    saveEnabled = saveEnabled,
                    bigFont = bigFont,
                    onSave = {
                        val fields = buildMap {
                            put(TeaFields.VARIETY, variety)
                            put(TeaFields.CUPS, cups)
                            put(TeaFields.AMOUNT, amount)
                            if (calories.isNotBlank()) put(TeaFields.CALORIES, calories)
                        }
                        val localDateTime = LocalDateTime.of(
                            selectedDate,
                            LocalTime.of(selectedHour, selectedMinute)
                        )
                        onSave(
                            RecordDraft(
                                typeCode = TeaFields.TYPE_CODE,
                                occurredAtEpochMillis = localDateTime.atZone(recordZone).toInstant().toEpochMilli(),
                                zoneId = recordZone.id,
                                note = note.trim(),
                                detail = RecordDetail.Generic(TeaFields.TYPE_CODE, fields)
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
        TeaTimePickerDialog(
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
private fun teaColors(): TeaColors {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        TeaColors(
            accent = Color(0xFFA8C3A3),
            accentContainer = Color(0xFF27362B),
            onAccentContainer = Color(0xFFD7E8D7),
            onAccent = Color(0xFF0E1510),
            hero = Color(0xFF17261C),
            heroAlt = Color(0xFF2C4635),
            onHero = Color(0xFFF4EBDA)
        )
    } else {
        TeaColors(
            accent = Color(0xFF4F715A),
            accentContainer = Color(0xFFE6EEE5),
            onAccentContainer = Color(0xFF294732),
            onAccent = Color.White,
            hero = Color(0xFF23382A),
            heroAlt = Color(0xFF385342),
            onHero = Color(0xFFF7F1E6)
        )
    }
}

@Composable
private fun TeaEditorTopBar(
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
                TeaMetaPill(dateText, Modifier, onDateClick)
                TeaMetaPill(timeText, Modifier, onTimeClick)
            }
        }
        if (bigFont) {
            TeaMetaPill(dateText, Modifier.fillMaxWidth(), onDateClick)
            TeaMetaPill(timeText, Modifier.fillMaxWidth(), onTimeClick)
        }
    }
}

@Composable
private fun TeaMetaPill(
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
private fun TeaHeroSummary(
    variety: String,
    cups: String,
    amount: String,
    calories: String,
    colors: TeaColors,
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
                    "TODAY'S TEA",
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
                            Icons.Filled.Spa,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = colors.onHero
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TEA LEAF",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = variety,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${cups.ifBlank { "—" }} 杯 · ${amount.ifBlank { "—" }} ml",
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
                    TeaHeroStat("本次", "${cups.ifBlank { "—" }} 杯", colors)
                    TeaHeroStat("份量", "${amount.ifBlank { "—" }} ml", colors)
                    TeaHeroStat("热量", if (calories.isBlank()) "未填写" else "$calories kcal", colors)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TeaHeroStat("本次", "${cups.ifBlank { "—" }} 杯", colors, Modifier.weight(1f))
                    TeaHeroStat("份量", "${amount.ifBlank { "—" }} ml", colors, Modifier.weight(1f))
                    TeaHeroStat("热量", if (calories.isBlank()) "—" else "$calories", colors, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TeaHeroStat(
    label: String,
    value: String,
    colors: TeaColors,
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
private fun TeaVarietySection(
    varieties: List<String>,
    selected: String,
    adding: Boolean,
    newVariety: String,
    colors: TeaColors,
    bigFont: Boolean,
    onSelect: (String) -> Unit,
    onToggleAdding: () -> Unit,
    onNewVarietyChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            TeaSectionHeading(kicker = "第一步", title = "今天喝什么茶")
            TextButton(onClick = onToggleAdding) {
                Text(if (adding) "收起" else "＋ 自定义")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            varieties.forEach { name ->
                FilterChip(
                    selected = name == selected,
                    onClick = { onSelect(name) },
                    label = { Text(name) },
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
        AnimatedVisibility(visible = adding) {
            if (bigFont) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newVariety,
                        onValueChange = onNewVarietyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("茶种名") },
                        singleLine = true
                    )
                    TextButton(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = newVariety.isNotBlank()
                    ) { Text("添加茶种") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newVariety,
                        onValueChange = onNewVarietyChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("茶种名") },
                        singleLine = true
                    )
                    TextButton(onClick = onSubmit, enabled = newVariety.isNotBlank()) {
                        Text("添加")
                    }
                }
            }
        }
    }
}

@Composable
private fun TeaQuickSection(
    cups: String,
    amount: String,
    colors: TeaColors,
    onSelect: (TeaQuickPair) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TeaSectionHeading(kicker = "QUICK CUP", title = "快捷份量")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            teaQuickPairs.forEach { pair ->
                FilterChip(
                    selected = cups == pair.cups.toString() && amount == pair.amount.toString(),
                    onClick = { onSelect(pair) },
                    label = { Text("${pair.label} · ${pair.amount} ml") },
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
private fun TeaQuantityCard(
    cups: String,
    amount: String,
    calories: String,
    cupsValid: Boolean,
    amountValid: Boolean,
    caloriesValid: Boolean,
    colors: TeaColors,
    bigFont: Boolean,
    onCupsChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onDecreaseCups: () -> Unit,
    onIncreaseCups: () -> Unit,
    onDecreaseAmount: () -> Unit,
    onIncreaseAmount: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (bigFont) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("杯数与份量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${cups.ifBlank { "—" }} 杯 · ${amount.ifBlank { "—" }} ml",
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
                    Text("杯数与份量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${cups.ifBlank { "—" }} 杯 · ${amount.ifBlank { "—" }} ml",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accent
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("几杯", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeaStepperButton("减少杯数", false, colors, onDecreaseCups)
                    OutlinedTextField(
                        value = cups,
                        onValueChange = onCupsChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("杯数") },
                        singleLine = true,
                        isError = !cupsValid,
                        supportingText = if (!cupsValid) {
                            { Text("请输入 ${TeaFields.CUPS_RANGE.first}–${TeaFields.CUPS_RANGE.last} 的整数") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    TeaStepperButton("增加杯数", true, colors, onIncreaseCups)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("多少毫升", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeaStepperButton("减少毫升", false, colors, onDecreaseAmount)
                    OutlinedTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("ml") },
                        singleLine = true,
                        isError = !amountValid,
                        supportingText = if (!amountValid) {
                            { Text("请输入 ${TeaFields.AMOUNT_RANGE.first}–${TeaFields.AMOUNT_RANGE.last} 的整数") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    TeaStepperButton("增加毫升", true, colors, onIncreaseAmount)
                }
            }
            OutlinedTextField(
                value = calories,
                onValueChange = onCaloriesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("热量 kcal（选填）") },
                singleLine = true,
                isError = !caloriesValid,
                supportingText = if (!caloriesValid) {
                    { Text("请输入 ${TeaFields.CALORIES_RANGE.first}–${TeaFields.CALORIES_RANGE.last} 的整数") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text(
                "热量单位为大卡，留空即可；自定义茶种默认不估热量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TeaStepperButton(
    description: String,
    increase: Boolean,
    colors: TeaColors,
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
private fun TeaSectionHeading(kicker: String, title: String) {
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
private fun TeaSaveBar(
    variety: String,
    cups: String,
    amount: String,
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
                TeaSaveSummary(variety, cups, amount)
                TeaSaveButton(saveEnabled, onSave, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TeaSaveSummary(variety, cups, amount, Modifier.weight(1f))
                TeaSaveButton(saveEnabled, onSave)
            }
        }
    }
}

@Composable
private fun TeaSaveSummary(
    variety: String,
    cups: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "准备保存",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$variety · ${cups.ifBlank { "—" }} 杯 · ${amount.ifBlank { "—" }} ml",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TeaSaveButton(
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
        Text("保存这杯茶", fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TeaTimePickerDialog(
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

private fun compactTeaDateLabel(date: LocalDate, today: LocalDate): String =
    if (date == today) "今天" else date.format(teaDateFormatter)
