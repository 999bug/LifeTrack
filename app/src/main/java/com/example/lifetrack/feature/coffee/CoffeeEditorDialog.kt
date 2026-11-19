package com.example.lifetrack.feature.coffee

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.RecordEditorDialog

/**
 * 咖啡编辑器：复用通用事件壳，详情字段按 v0.7 口径重做。
 *
 * - 品种为「预设 + 用户自定义」的动态清单，可现场新增并立即选中；
 * - 杯型选中后带出毫升，允许手改；
 * - 热量选填，选中预设品种时带出估值，用户改动后不再覆盖。
 *
 * 旧字段（drinkType/unit/caffeine）不再出现在这里；历史记录打开编辑时会用
 * 新字段的默认值初始化，保存后即升级为新格式。
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
    // 记住上一次由品种自动带出的热量：用户手改后就不再跟随品种覆盖。
    var lastAutoCalories by remember(record) {
        mutableStateOf(CoffeeFields.defaultCalories(initialVariety)?.toString().orEmpty())
    }

    val allVarieties = CoffeeFields.PRESET_VARIETIES + customVarieties.filterNot { it in CoffeeFields.PRESET_VARIETIES }
    var addingVariety by remember(record) { mutableStateOf(false) }
    var newVariety by remember(record) { mutableStateOf("") }

    fun selectVariety(value: String) {
        variety = value
        // 只有当前热量还等于上一次自动值（或为空）时才跟随品种更新。
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

    val amountValid = CoffeeFields.isValidAmount(amount)
    val caloriesValid = calories.isEmpty() || CoffeeFields.isValidCalories(calories)
    val saveEnabled = amountValid && caloriesValid && variety.isNotBlank()

    RecordEditorDialog(
        title = if (record == null) "记一杯咖啡" else "编辑咖啡记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        saveEnabled = saveEnabled,
        onDismiss = onDismiss,
        onSave = { value ->
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
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    note = value.note,
                    detail = RecordDetail.Generic(CoffeeFields.TYPE_CODE, fields)
                )
            )
        }
    ) {
        Text("品种", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            allVarieties.forEach { name ->
                FilterChip(
                    selected = name == variety,
                    onClick = { selectVariety(name) },
                    label = { Text(name) }
                )
            }
            FilterChip(
                selected = addingVariety,
                onClick = { addingVariety = !addingVariety },
                label = { Text("＋ 自定义") }
            )
        }
        if (addingVariety) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = newVariety,
                    onValueChange = { newVariety = it.take(CoffeeFields.MAX_VARIETY_LENGTH) },
                    modifier = Modifier.weight(1f),
                    label = { Text("品种名") },
                    singleLine = true
                )
                TextButton(onClick = { submitVariety() }) { Text("添加") }
            }
        }

        Text("杯型", style = MaterialTheme.typography.titleSmall)
        OptionCardGroup(
            values = CoffeeFields.SIZES,
            selected = size,
            label = CoffeeFields::sizeLabel,
            onSelect = { chosen ->
                size = chosen
                CoffeeFields.SIZE_ML[chosen]?.let { amount = it.toString() }
            },
            description = { "${CoffeeFields.SIZE_ML[it] ?: ""}ml" },
            price = { if (it == "xlarge") "+¥3" else null },
            badge = { if (it == "xlarge") "容量加大约30%" else null }
        )

        Text("温度", style = MaterialTheme.typography.titleSmall)
        OptionCardGroup(
            values = CoffeeFields.TEMPERATURES,
            selected = temperature,
            label = CoffeeFields::temperatureLabel,
            onSelect = { temperature = it }
        )

        Text("咖啡豆", style = MaterialTheme.typography.titleSmall)
        OptionCardGroup(
            values = CoffeeFields.BEANS_VALUES,
            selected = beans,
            label = CoffeeFields::beansLabel,
            onSelect = { beans = it },
            description = {
                when (it) {
                    "espresso" -> "坚果基线 · 香醇口感"
                    "dark" -> "黑巧尾韵 · 浓郁口感"
                    else -> null
                }
            },
            badge = { if (it == "espresso") "金奖拼配" else if (it == "dark") "深烘" else null },
            badgePrimary = { it == "espresso" }
        )

        Text("咖啡浓度", style = MaterialTheme.typography.titleSmall)
        OptionCardGroup(
            values = CoffeeFields.CONCENTRATIONS,
            selected = concentration,
            label = CoffeeFields::concentrationLabel,
            onSelect = { concentration = it },
            price = { if (it == "extra") "+¥3" else null }
        )

        Text("糖度", style = MaterialTheme.typography.titleSmall)
        OptionCardGroup(
            values = CoffeeFields.SUGARS,
            selected = sugar,
            label = CoffeeFields::sugarLabel,
            onSelect = { sugar = it },
            badge = { if (it == "none") "推荐" else null }
        )

        Text("奶油", style = MaterialTheme.typography.titleSmall)
        OptionCardGroup(
            values = CoffeeFields.CREAMS,
            selected = cream,
            label = CoffeeFields::creamLabel,
            onSelect = { cream = it },
            price = { if (it == "added") "+¥3" else null }
        )

        Text("热量（选填）", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = calories,
            onValueChange = { calories = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("大卡 kcal") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !caloriesValid,
            supportingText = {
                if (!caloriesValid) {
                    Text("请输入 ${CoffeeFields.CALORIES_RANGE.first}–${CoffeeFields.CALORIES_RANGE.last} 之间的整数")
                } else {
                    Text("选填；选中预设品种时会自动带出估值，可手动改")
                }
            }
        )

        Text("份量（毫升）", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ml") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = amount.isNotEmpty() && !amountValid,
            supportingText = {
                if (amount.isNotEmpty() && !amountValid) {
                    Text("请输入 ${CoffeeFields.AMOUNT_RANGE.first}–${CoffeeFields.AMOUNT_RANGE.last} 之间的整数")
                }
            }
        )

        Text(
            "份量随杯型带出、可手改；热量单位为大卡。价格标签仅作选项说明，不记入账本。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
