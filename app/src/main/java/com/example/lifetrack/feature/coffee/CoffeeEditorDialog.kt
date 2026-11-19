@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.lifetrack.feature.coffee

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalCafe
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
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
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.PickerDateMapper
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.domain.model.RecordDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val compactDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

/**
 * 咖啡编辑器：全屏「点单笺」布局。
 *
 * 对外签名保持不变，导航继续使用同一个入口；数据字段、校验与保存口径均沿用
 * [CoffeeFields]。本页只重做信息层级与录入方式，不改变 Room 或备份格式。
 */
@Composable
fun CoffeeEditorDialog(
    record: BackupRecord?,
    customVarieties: List<String>,
    onAddVariety: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val initialVariety = initial[CoffeeFields.VARIETY] ?: CoffeeFields.PRESET_VARIETIES.first()
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
    var size by remember(record) {
        mutableStateOf(initial[CoffeeFields.SIZE]?.takeIf { it in CoffeeFields.SIZES } ?: "large")
    }
    var temperature by remember(record) {
        mutableStateOf(initial[CoffeeFields.TEMPERATURE]?.takeIf { it in CoffeeFields.TEMPERATURES } ?: "ice")
    }
    var beans by remember(record) {
        mutableStateOf(initial[CoffeeFields.BEANS]?.takeIf { it in CoffeeFields.BEANS_VALUES } ?: "espresso")
    }
    var concentration by remember(record) {
        mutableStateOf(initial[CoffeeFields.CONCENTRATION]?.takeIf { it in CoffeeFields.CONCENTRATIONS } ?: "normal")
    }
    var sugar by remember(record) {
        mutableStateOf(initial[CoffeeFields.SUGAR]?.takeIf { it in CoffeeFields.SUGARS } ?: "none")
    }
    var cream by remember(record) {
        mutableStateOf(initial[CoffeeFields.CREAM]?.takeIf { it in CoffeeFields.CREAMS } ?: "none")
    }
    var calories by remember(record) {
        mutableStateOf(initial[CoffeeFields.CALORIES] ?: CoffeeFields.defaultCalories(initialVariety)?.toString().orEmpty())
    }
    var amount by remember(record) {
        mutableStateOf(initial[CoffeeFields.AMOUNT] ?: CoffeeFields.SIZE_ML["large"].toString())
    }
    var lastAutoCalories by remember(record) {
        mutableStateOf(CoffeeFields.defaultCalories(initialVariety)?.toString().orEmpty())
    }
    var selectedDate by remember(record) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember(record) { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by remember(record) { mutableIntStateOf(initialDateTime.minute) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAmountDetails by remember { mutableStateOf(false) }
    var addingVariety by remember(record) { mutableStateOf(false) }
    var newVariety by remember(record) { mutableStateOf("") }

    val allVarieties = CoffeeFields.PRESET_VARIETIES + customVarieties.filterNot { it in CoffeeFields.PRESET_VARIETIES }
    val amountValid = CoffeeFields.isValidAmount(amount)
    val caloriesValid = calories.isEmpty() || CoffeeFields.isValidCalories(calories)
    val saveEnabled = amountValid && caloriesValid && variety.isNotBlank()

    fun selectVariety(value: String) {
        variety = value
        if (calories.isEmpty() || calories == lastAutoCalories) {
            val auto = CoffeeFields.defaultCalories(value)?.toString().orEmpty()
            calories = auto
            lastAutoCalories = auto
        }
    }

    fun submitVariety() {
        val name = newVariety.trim()
        if (name.isNotEmpty() && CoffeeFields.isValidVariety(name)) {
            onAddVariety(name)
            selectVariety(name)
            newVariety = ""
            addingVariety = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val colors = coffeeColors()
        val timeText = String.format(Locale.CHINA, "%02d:%02d", selectedHour, selectedMinute)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                CoffeeEditorTopBar(
                    title = if (record == null) "记一杯咖啡" else "编辑咖啡",
                    dateText = compactDateLabel(selectedDate, LocalDate.now()),
                    timeText = timeText,
                    colors = colors,
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
                        CoffeeHeroTicket(
                            variety = variety,
                            meta = coffeeMeta(size, temperature, sugar),
                            amount = amount,
                            calories = calories,
                            concentration = concentration,
                            colors = colors
                        )
                    }
                    item {
                        CoffeeVarietySection(
                            varieties = allVarieties,
                            selected = variety,
                            adding = addingVariety,
                            newVariety = newVariety,
                            colors = colors,
                            onSelect = ::selectVariety,
                            onToggleAdding = { addingVariety = !addingVariety },
                            onNewVarietyChange = { newVariety = it.take(CoffeeFields.MAX_VARIETY_LENGTH) },
                            onSubmit = ::submitVariety
                        )
                    }
                    item {
                        CoffeePreferencesCard(
                            size = size,
                            temperature = temperature,
                            beans = beans,
                            concentration = concentration,
                            sugar = sugar,
                            cream = cream,
                            colors = colors,
                            onSize = { chosen ->
                                size = chosen
                                CoffeeFields.SIZE_ML[chosen]?.let { amount = it.toString() }
                            },
                            onTemperature = { temperature = it },
                            onBeans = { beans = it },
                            onConcentration = { concentration = it },
                            onSugar = { sugar = it },
                            onCream = { cream = if (it) "added" else "none" }
                        )
                    }
                    item {
                        CoffeeAmountCard(
                            expanded = showAmountDetails,
                            amount = amount,
                            calories = calories,
                            amountValid = amountValid,
                            caloriesValid = caloriesValid,
                            onToggle = { showAmountDetails = !showAmountDetails },
                            onAmountChange = { amount = it.filter(Char::isDigit) },
                            onCaloriesChange = { calories = it.filter(Char::isDigit).take(4) }
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeading(kicker = "可选", title = "留一句备注")
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("备注") },
                                placeholder = { Text("比如：午后提神，搭配了一块可颂") },
                                minLines = 2,
                                maxLines = 5
                            )
                        }
                    }
                }
                CoffeeSaveBar(
                    variety = variety,
                    meta = coffeeMeta(size, temperature, sugar),
                    saveEnabled = saveEnabled,
                    onSave = {
                        val localDateTime = LocalDateTime.of(
                            selectedDate,
                            LocalTime.of(selectedHour, selectedMinute)
                        )
                        val fields = buildMap {
                            put(CoffeeFields.VARIETY, variety)
                            put(CoffeeFields.SIZE, size)
                            put(CoffeeFields.TEMPERATURE, temperature)
                            put(CoffeeFields.BEANS, beans)
                            put(CoffeeFields.CONCENTRATION, concentration)
                            put(CoffeeFields.SUGAR, sugar)
                            put(CoffeeFields.CREAM, cream)
                            put(CoffeeFields.AMOUNT, amount)
                            if (calories.isNotBlank()) put(CoffeeFields.CALORIES, calories)
                        }
                        onSave(
                            RecordDraft(
                                typeCode = CoffeeFields.TYPE_CODE,
                                occurredAtEpochMillis = localDateTime.atZone(recordZone).toInstant().toEpochMilli(),
                                zoneId = recordZone.id,
                                note = note.trim(),
                                detail = RecordDetail.Generic(CoffeeFields.TYPE_CODE, fields)
                            )
                        )
                    }
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
                    datePickerState.selectedDateMillis?.let { selectedDate = PickerDateMapper.fromUtcMillis(it) }
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
        CoffeeTimePickerDialog(
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

private data class CoffeeColors(
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val onAccent: Color,
    val hero: Color,
    val onHero: Color
)

@Composable
private fun coffeeColors(): CoffeeColors {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CoffeeColors(
            accent = Color(0xFFD0A06D),
            accentContainer = Color(0xFF49382C),
            onAccentContainer = Color(0xFFF4E1C7),
            onAccent = Color(0xFF221710),
            hero = Color(0xFF251A16),
            onHero = Color(0xFFF4E1C7)
        )
    } else {
        CoffeeColors(
            accent = Color(0xFF95653D),
            accentContainer = Color(0xFFF0E1CE),
            onAccentContainer = Color(0xFF5E3D25),
            onAccent = Color.White,
            hero = Color(0xFF2F211B),
            onHero = Color(0xFFF7EAD8)
        )
    }
}

@Composable
private fun CoffeeEditorTopBar(
    title: String,
    dateText: String,
    timeText: String,
    colors: CoffeeColors,
    onClose: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    val bigFont = LocalDensity.current.fontScale > 1.3f
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!bigFont) {
                CoffeeMetaPill(dateText, onDateClick)
                CoffeeMetaPill(timeText, onTimeClick)
            }
        }
        if (bigFont) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoffeeMetaPill(dateText, onDateClick, Modifier.weight(1f))
                CoffeeMetaPill(timeText, onTimeClick, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoffeeMetaPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun CoffeeHeroTicket(
    variety: String,
    meta: String,
    amount: String,
    calories: String,
    concentration: String,
    colors: CoffeeColors
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
                    "今日咖啡 · 第 1 杯",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onHero.copy(alpha = 0.64f),
                    letterSpacing = 1.2.sp
                )
                Text(
                    "NO. ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd"))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onHero.copy(alpha = 0.58f),
                    letterSpacing = 1.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, colors.onHero.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.LocalCafe,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = colors.onHero
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TODAY'S ORDER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFDDB77E),
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = variety,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onHero.copy(alpha = 0.72f)
                    )
                }
            }
            HorizontalDivider(color = colors.onHero.copy(alpha = 0.16f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroStat(calories.ifBlank { "—" }, "KCAL", Modifier.weight(1f), colors)
                HeroStat(amount.ifBlank { "—" }, "ML", Modifier.weight(1f), colors)
                HeroStat(if (concentration == "extra") "2" else "1", "份浓缩", Modifier.weight(1f), colors)
            }
        }
    }
}

@Composable
private fun HeroStat(
    value: String,
    label: String,
    modifier: Modifier,
    colors: CoffeeColors
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            modifier = Modifier.padding(bottom = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onHero.copy(alpha = 0.58f),
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeading(kicker: String, title: String) {
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
private fun CoffeeVarietySection(
    varieties: List<String>,
    selected: String,
    adding: Boolean,
    newVariety: String,
    colors: CoffeeColors,
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
            SectionHeading(kicker = "第一步", title = "今天喝什么")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newVariety,
                    onValueChange = onNewVarietyChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("品种名") },
                    singleLine = true
                )
                TextButton(onClick = onSubmit, enabled = newVariety.isNotBlank()) {
                    Text("添加")
                }
            }
        }
    }
}

@Composable
private fun CoffeePreferencesCard(
    size: String,
    temperature: String,
    beans: String,
    concentration: String,
    sugar: String,
    cream: String,
    colors: CoffeeColors,
    onSize: (String) -> Unit,
    onTemperature: (String) -> Unit,
    onBeans: (String) -> Unit,
    onConcentration: (String) -> Unit,
    onSugar: (String) -> Unit,
    onCream: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            PreferenceBlock(title = "杯型", value = "${CoffeeFields.sizeLabel(size)} · ${CoffeeFields.SIZE_ML[size]} ml") {
                CoffeeChoiceRow(
                    options = CoffeeFields.SIZES.map { value ->
                        ChoiceOption(
                            value = value,
                            title = CoffeeFields.sizeLabel(value),
                            subtitle = buildString {
                                append(CoffeeFields.SIZE_ML[value])
                                append(" ml")
                                if (value == "xlarge") append(" · +¥3")
                            }
                        )
                    },
                    selected = size,
                    colors = colors,
                    onSelect = onSize
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceBlock(title = "温度", value = CoffeeFields.temperatureLabel(temperature)) {
                CoffeeChoiceRow(
                    options = listOf(
                        ChoiceOption("ice", "冰", "清爽"),
                        ChoiceOption("hot", "热", "暖香")
                    ),
                    selected = temperature,
                    colors = colors,
                    onSelect = onTemperature
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceBlock(title = "咖啡豆", value = CoffeeFields.beansLabel(beans)) {
                CoffeeChoiceRow(
                    options = listOf(
                        ChoiceOption("espresso", "意式拼配", "坚果 · 香醇"),
                        ChoiceOption("dark", "深烘拼配", "黑巧 · 浓郁")
                    ),
                    selected = beans,
                    colors = colors,
                    onSelect = onBeans
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceBlock(title = "浓度", value = CoffeeFields.concentrationLabel(concentration)) {
                CoffeeChoiceRow(
                    options = listOf(
                        ChoiceOption("normal", "标准", "默认浓度"),
                        ChoiceOption("extra", "加浓", "多一份浓缩 · +¥3")
                    ),
                    selected = concentration,
                    colors = colors,
                    onSelect = onConcentration
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceBlock(title = "糖度", value = CoffeeFields.sugarLabel(sugar)) {
                SugarSelector(selected = sugar, colors = colors, onSelect = onSugar)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("奶油", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (cream == "added") "加奶油" else "不加奶油",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = cream == "added", onCheckedChange = onCream)
            }
        }
    }
}

private data class ChoiceOption(
    val value: String,
    val title: String,
    val subtitle: String
)

@Composable
private fun PreferenceBlock(
    title: String,
    value: String,
    content: @Composable () -> Unit
) {
    val bigFont = LocalDensity.current.fontScale > 1.3f
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        if (bigFont) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun CoffeeChoiceRow(
    options: List<ChoiceOption>,
    selected: String,
    colors: CoffeeColors,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            CoffeeChoice(
                option = option,
                selected = option.value == selected,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(option.value) }
            )
        }
    }
}

@Composable
private fun CoffeeChoice(
    option: ChoiceOption,
    selected: Boolean,
    colors: CoffeeColors,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accentContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                option.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onAccentContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                option.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SugarSelector(
    selected: String,
    colors: CoffeeColors,
    onSelect: (String) -> Unit
) {
    val bigFont = LocalDensity.current.fontScale > 1.3f
    val options = CoffeeFields.SUGARS.map { value ->
        value to when (value) {
            "standard" -> "标准"
            "less" -> "少甜"
            "lighter" -> "少少"
            "micro" -> "微甜"
            else -> "不加糖"
        }
    }
    val chunks = options.chunked(if (bigFont) 3 else 5)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chunks.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEach { (value, label) ->
                    val selectedStep = value == selected
                    Surface(
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedStep) colors.accentContainer else Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(18.dp),
                                shape = CircleShape,
                                color = if (selectedStep) colors.accent else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedStep) colors.accent else MaterialTheme.colorScheme.outline
                                )
                            ) {}
                            Spacer(Modifier.height(4.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedStep) colors.onAccentContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
                repeat((if (bigFont) 3 else 5) - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CoffeeAmountCard(
    expanded: Boolean,
    amount: String,
    calories: String,
    amountValid: Boolean,
    caloriesValid: Boolean,
    onToggle: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit
) {
    val bigFont = LocalDensity.current.fontScale > 1.3f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("份量与热量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (calories.isBlank()) "$amount ml · 热量待填" else "$amount ml · $calories kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起份量与热量" else "展开份量与热量",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
            AnimatedVisibility(visible = expanded) {
                if (bigFont) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AmountField(amount, amountValid, onAmountChange, Modifier.fillMaxWidth())
                        CaloriesField(calories, caloriesValid, onCaloriesChange, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AmountField(amount, amountValid, onAmountChange, Modifier.weight(1f))
                        CaloriesField(calories, caloriesValid, onCaloriesChange, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountField(
    amount: String,
    valid: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = amount,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("份量 ml") },
        singleLine = true,
        isError = amount.isNotEmpty() && !valid,
        supportingText = if (amount.isNotEmpty() && !valid) {
            { Text("请输入 ${CoffeeFields.AMOUNT_RANGE.first}–${CoffeeFields.AMOUNT_RANGE.last}") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun CaloriesField(
    calories: String,
    valid: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = calories,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("热量 kcal（选填）") },
        singleLine = true,
        isError = !valid,
        supportingText = if (!valid) {
            { Text("请输入 ${CoffeeFields.CALORIES_RANGE.first}–${CoffeeFields.CALORIES_RANGE.last}") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun CoffeeSaveBar(
    variety: String,
    meta: String,
    saveEnabled: Boolean,
    onSave: () -> Unit
) {
    val bigFont = LocalDensity.current.fontScale > 1.3f
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
                CoffeeSaveSummary(variety, meta)
                CoffeeSaveButton(saveEnabled, onSave, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoffeeSaveSummary(variety, meta, Modifier.weight(1f))
                CoffeeSaveButton(saveEnabled, onSave)
            }
        }
    }
}

@Composable
private fun CoffeeSaveSummary(
    variety: String,
    meta: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            variety,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            meta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoffeeSaveButton(
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
        Text("保存这杯", fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CoffeeTimePickerDialog(
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

private fun coffeeMeta(size: String, temperature: String, sugar: String): String =
    "${CoffeeFields.sizeLabel(size)} · ${CoffeeFields.temperatureLabel(temperature)} · ${CoffeeFields.sugarLabel(sugar)}"

private fun compactDateLabel(date: LocalDate, today: LocalDate): String =
    if (date == today) "今天" else date.format(compactDateFormatter)
