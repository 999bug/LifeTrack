package com.example.lifetrack.feature.sport

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
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.ChoiceChipRow
import com.example.lifetrack.feature.record.RecordEditorDialog

/**
 * 运动编辑器：复用通用事件壳，只提供类型 / 时长 / 强度三个详情字段。
 * 时长是唯一必填项，类型与强度都有默认值，避免大字号下多一步操作才能保存。
 */
@Composable
fun SportEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    var sport by remember(record) { mutableStateOf(initial[SportFields.SPORT] ?: SportFields.SPORTS.first()) }
    var duration by remember(record) { mutableStateOf(initial[SportFields.DURATION].orEmpty()) }
    var intensity by remember(record) {
        mutableStateOf(initial[SportFields.INTENSITY] ?: SportFields.INTENSITIES[1])
    }
    val valid = SportFields.isValidDuration(duration)

    RecordEditorDialog(
        title = if (record == null) "记录运动" else "编辑运动记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        saveEnabled = valid,
        onDismiss = onDismiss,
        onSave = { value ->
            onSave(
                RecordDraft(
                    typeCode = SportFields.TYPE_CODE,
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    note = value.note,
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
    ) {
        Text("运动类型", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(SportFields.SPORTS, sport, SportFields::sportLabel) { sport = it }

        Text(
            "时长（必填，${SportFields.DURATION_RANGE.first}–${SportFields.DURATION_RANGE.last} 分钟）",
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("分钟") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = duration.isNotEmpty() && !valid
        )
        if (duration.isNotEmpty() && !valid) {
            Text(
                "请输入 ${SportFields.DURATION_RANGE.first}–${SportFields.DURATION_RANGE.last} 之间的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text("强度", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(SportFields.INTENSITIES, intensity, SportFields::intensityLabel) { intensity = it }

        Text(
            "时长按分钟记录，展示时超过 60 分钟会自动换算成小时；不做热量或配速推算。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
