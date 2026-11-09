package com.example.lifetrack.feature.coffee

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.ChoiceChipRow
import com.example.lifetrack.feature.record.RecordEditorDialog

/**
 * 咖啡 / 茶编辑器：复用通用事件壳，只提供四个详情字段。
 * 份量与单位不做换算，同一条记录在列表与备份里始终使用同一个数值。
 */
@Composable
fun CoffeeEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    var drink by remember(record) { mutableStateOf(initial[CoffeeFields.DRINK] ?: CoffeeFields.DRINKS.first()) }
    var amount by remember(record) { mutableStateOf(initial[CoffeeFields.AMOUNT].orEmpty()) }
    var unit by remember(record) { mutableStateOf(initial[CoffeeFields.UNIT] ?: CoffeeFields.UNITS.first()) }
    var caffeine by remember(record) {
        mutableStateOf(initial[CoffeeFields.CAFFEINE] ?: CoffeeFields.CAFFEINE_LEVELS.first())
    }
    val valid = CoffeeFields.isValidAmount(amount)

    RecordEditorDialog(
        title = if (record == null) "记录咖啡 / 茶" else "编辑咖啡 / 茶记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        saveEnabled = valid,
        onDismiss = onDismiss,
        onSave = { value ->
            onSave(
                RecordDraft(
                    typeCode = CoffeeFields.TYPE_CODE,
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    note = value.note,
                    detail = RecordDetail.Generic(
                        CoffeeFields.TYPE_CODE,
                        mapOf(
                            CoffeeFields.DRINK to drink,
                            CoffeeFields.AMOUNT to amount,
                            CoffeeFields.UNIT to unit,
                            CoffeeFields.CAFFEINE to caffeine
                        )
                    )
                )
            )
        }
    ) {
        Text("饮品类型", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(CoffeeFields.DRINKS, drink, CoffeeFields::drinkLabel) { drink = it }

        Text(
            "份量（必填，${CoffeeFields.AMOUNT_RANGE.first}–${CoffeeFields.AMOUNT_RANGE.last}）",
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("份量") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = amount.isNotEmpty() && !valid
        )
        if (amount.isNotEmpty() && !valid) {
            Text(
                "请输入 ${CoffeeFields.AMOUNT_RANGE.first}–${CoffeeFields.AMOUNT_RANGE.last} 之间的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text("单位", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(CoffeeFields.UNITS, unit, CoffeeFields::unitLabel) { unit = it }

        Text("咖啡因", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(CoffeeFields.CAFFEINE_LEVELS, caffeine, CoffeeFields::caffeineLabel) { caffeine = it }

        Text(
            "单位仅用于显示，不做换算；份量在同一条记录的应用内展示与备份里保持一致。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
