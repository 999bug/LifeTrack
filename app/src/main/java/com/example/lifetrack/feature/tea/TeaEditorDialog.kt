package com.example.lifetrack.feature.tea

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.TeaFields
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.RecordEditorDialog

/**
 * 茶编辑器：v0.7 从 coffee 拆出的独立类型。
 * 字段克制——茶种（预设 + 自定义）、几杯、多少毫升、热量（选填）。
 * 没有咖啡的杯型 / 豆子 / 糖度那一套。
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

    var variety by remember(record) { mutableStateOf(initialVariety) }
    var cups by remember(record) {
        mutableStateOf(initial[TeaFields.CUPS] ?: "1")
    }
    var amount by remember(record) {
        mutableStateOf(initial[TeaFields.AMOUNT] ?: "300")
    }
    var calories by remember(record) {
        mutableStateOf(initial[TeaFields.CALORIES] ?: TeaFields.defaultCalories(initialVariety)?.toString().orEmpty())
    }
    var lastAutoCalories by remember(record) {
        mutableStateOf(TeaFields.defaultCalories(initialVariety)?.toString().orEmpty())
    }

    val allVarieties = TeaFields.PRESET_VARIETIES + customVarieties.filterNot { it in TeaFields.PRESET_VARIETIES }
    var addingVariety by remember(record) { mutableStateOf(false) }
    var newVariety by remember(record) { mutableStateOf("") }

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

    val cupsValid = TeaFields.isValidCups(cups)
    val amountValid = TeaFields.isValidAmount(amount)
    val caloriesValid = calories.isEmpty() || TeaFields.isValidCalories(calories)
    val saveEnabled = cupsValid && amountValid && caloriesValid && variety.isNotBlank()

    RecordEditorDialog(
        title = if (record == null) "记一杯茶" else "编辑茶记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        saveEnabled = saveEnabled,
        onDismiss = onDismiss,
        onSave = { value ->
            val fields = buildMap {
                put(TeaFields.VARIETY, variety)
                put(TeaFields.CUPS, cups)
                put(TeaFields.AMOUNT, amount)
                if (calories.isNotBlank()) put(TeaFields.CALORIES, calories)
            }
            onSave(
                RecordDraft(
                    typeCode = TeaFields.TYPE_CODE,
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    note = value.note,
                    detail = RecordDetail.Generic(TeaFields.TYPE_CODE, fields)
                )
            )
        }
    ) {
        Text("茶种", style = MaterialTheme.typography.titleSmall)
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
                    onValueChange = { newVariety = it.take(TeaFields.MAX_VARIETY_LENGTH) },
                    modifier = Modifier.weight(1f),
                    label = { Text("茶种名") },
                    singleLine = true
                )
                TextButton(onClick = { submitVariety() }) { Text("添加") }
            }
        }

        Text("几杯", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = cups,
            onValueChange = { cups = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("杯数") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !cupsValid,
            supportingText = {
                if (!cupsValid) Text("请输入 ${TeaFields.CUPS_RANGE.first}–${TeaFields.CUPS_RANGE.last} 之间的整数")
            }
        )

        Text("多少毫升", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ml") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !amountValid,
            supportingText = {
                if (!amountValid) Text("请输入 ${TeaFields.AMOUNT_RANGE.first}–${TeaFields.AMOUNT_RANGE.last} 之间的整数")
            }
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
                if (!caloriesValid) Text("请输入 ${TeaFields.CALORIES_RANGE.first}–${TeaFields.CALORIES_RANGE.last} 之间的整数")
            }
        )

        Text(
            "热量单位为大卡，选填。茶种可自定义，添加后下次仍在。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
