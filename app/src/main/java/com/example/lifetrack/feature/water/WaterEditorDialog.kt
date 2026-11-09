package com.example.lifetrack.feature.water

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.RecordEditorDialog

/**
 * 饮水编辑器：复用通用事件壳，只提供「数量 + 单位」两个详情字段。
 * 单位仅影响显示，不做换算，避免同一条记录在列表与备份里出现两套数值。
 */
@Composable
fun WaterEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    var amount by remember(record) { mutableStateOf(initial[WaterFields.AMOUNT].orEmpty()) }
    var unit by remember(record) { mutableStateOf(initial[WaterFields.UNIT] ?: WaterFields.UNITS.first()) }
    val valid = WaterFields.isValidAmount(amount)

    RecordEditorDialog(
        title = if (record == null) "记录饮水" else "编辑饮水记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        saveEnabled = valid,
        onDismiss = onDismiss,
        onSave = { value ->
            onSave(
                RecordDraft(
                    typeCode = WaterFields.TYPE_CODE,
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    note = value.note,
                    detail = RecordDetail.Generic(
                        WaterFields.TYPE_CODE,
                        mapOf(WaterFields.AMOUNT to amount, WaterFields.UNIT to unit)
                    )
                )
            )
        }
    ) {
        Text(
            "数量（必填，${WaterFields.AMOUNT_RANGE.first}–${WaterFields.AMOUNT_RANGE.last}）",
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("饮用量") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = amount.isNotEmpty() && !valid
        )
        Text("单位", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WaterFields.UNITS.forEach { value ->
                FilterChip(
                    selected = unit == value,
                    onClick = { unit = value },
                    label = { Text(WaterFields.unitLabel(value)) }
                )
            }
        }
        Text(
            "单位仅用于显示，不做换算；同一条记录在整个应用与备份里使用同一个数值。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (amount.isNotEmpty() && !valid) {
            Text(
                "请输入 ${WaterFields.AMOUNT_RANGE.first}–${WaterFields.AMOUNT_RANGE.last} 之间的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
