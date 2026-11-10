package com.example.lifetrack.feature.intimacy

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
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.ChoiceChipRow
import com.example.lifetrack.feature.record.RecordEditorDialog

/** 编辑器里「未记录」的占位值，只用于 chip 选中态，绝不写入详情 map。 */
private const val NO_ENTRY = ""

/**
 * 亲密关系编辑器：复用通用事件壳，提供三个均可空的详情字段。
 *
 * 三项都不填时整条记录不带 detail，与恢复路径的「无详情仍合法」保持一致；
 * 复用 [ChoiceChipRow] 的大字号分行逻辑，不另写一套 chips。
 */
@Composable
fun IntimacyEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    var protection by remember(record) { mutableStateOf<String?>(initial[IntimacyFields.PROTECTION]) }
    var experience by remember(record) { mutableStateOf<String?>(initial[IntimacyFields.EXPERIENCE]) }
    var duration by remember(record) { mutableStateOf(initial[IntimacyFields.DURATION].orEmpty()) }
    val durationValid = duration.isEmpty() || IntimacyFields.isValidDuration(duration)

    RecordEditorDialog(
        title = if (record == null) "记录亲密关系" else "编辑亲密关系记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        saveEnabled = durationValid,
        onDismiss = onDismiss,
        onSave = { value ->
            val fields = buildMap {
                protection?.let { put(IntimacyFields.PROTECTION, it) }
                experience?.let { put(IntimacyFields.EXPERIENCE, it) }
                if (duration.isNotEmpty()) put(IntimacyFields.DURATION, duration)
            }
            onSave(
                RecordDraft(
                    typeCode = IntimacyFields.TYPE_CODE,
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    note = value.note,
                    detail = if (fields.isEmpty()) {
                        null
                    } else {
                        RecordDetail.Generic(IntimacyFields.TYPE_CODE, fields)
                    }
                )
            )
        }
    ) {
        Text("保护措施", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(
            values = listOf(NO_ENTRY) + IntimacyFields.PROTECTION_VALUES,
            selected = protection ?: NO_ENTRY,
            label = { if (it == NO_ENTRY) "未记录" else IntimacyFields.protectionLabel(it) },
            onSelect = { protection = it.takeIf { selected -> selected != NO_ENTRY } }
        )

        Text("主观感受", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(
            values = listOf(NO_ENTRY) + IntimacyFields.EXPERIENCE_VALUES,
            selected = experience ?: NO_ENTRY,
            label = { if (it == NO_ENTRY) "未记录" else IntimacyFields.experienceLabel(it) },
            onSelect = { experience = it.takeIf { selected -> selected != NO_ENTRY } }
        )

        Text(
            "时长（可选，${IntimacyFields.DURATION_RANGE.first}–${IntimacyFields.DURATION_RANGE.last} 分钟）",
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("分钟") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !durationValid
        )
        if (!durationValid) {
            Text(
                "请输入 ${IntimacyFields.DURATION_RANGE.first}–${IntimacyFields.DURATION_RANGE.last} 之间的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            "三项均可不填。本模块不记录伴侣姓名或可识别身份的信息；建议在「设置 → 安全」开启应用锁。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
