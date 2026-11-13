package com.example.lifetrack.feature.sport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.ChoiceChipRow
import com.example.lifetrack.feature.record.RecordEditorDialog

/** 时长快捷预设：覆盖最常见的单次时长，点一下填入，仍可手改。 */
private val DURATION_PRESETS = listOf(15, 30, 45, 60, 90)

/**
 * 运动编辑器：复用通用事件壳，只提供类型 / 时长 / 强度三个详情字段。
 * 时长是唯一必填项，类型与强度都有默认值，避免大字号下多一步操作才能保存。
 *
 * v0.7 起界面只提供瑜伽 / 骑行 / 跑步三类（[SportFields.SPORTS]），并且：
 * - [initialSport] 让子应用的类型卡直接预选，点「瑜伽」后只需填时长；
 * - 打开旧类型记录时**不预选任何 chip**，先提示再让用户显式重选，
 *   否则 `ChoiceChipRow` 会出现三个都不选中、保存后静默把类型改掉的情况。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit,
    initialSport: String? = null
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    val storedSport = initial[SportFields.SPORT]?.takeIf { it.isNotEmpty() }
    var sport by remember(record, initialSport) {
        mutableStateOf(
            when {
                storedSport != null && SportFields.isSelectable(storedSport) -> storedSport
                storedSport != null -> "" // 旧类型：不预选，逼一次显式选择
                initialSport != null && SportFields.isSelectable(initialSport) -> initialSport
                else -> ""
            }
        )
    }
    var duration by remember(record) { mutableStateOf(initial[SportFields.DURATION].orEmpty()) }
    var intensity by remember(record) {
        mutableStateOf(initial[SportFields.INTENSITY] ?: SportFields.INTENSITIES[1])
    }
    val legacySport = storedSport?.takeIf { SportFields.isLegacy(it) }
    val durationValid = SportFields.isValidDuration(duration)
    val valid = durationValid && sport.isNotEmpty()

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
        if (legacySport != null) {
            Text(
                text = "这条记录的类型是「${SportFields.sportLabel(legacySport)}」，该类型已从界面下线。" +
                    "请重新选一个类型再保存；直接关掉不会改动它。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text("运动类型", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SportFields.SPORTS.forEach { value ->
                FilterChip(
                    selected = sport == value,
                    onClick = { sport = value },
                    label = { Text(SportFields.sportLabel(value)) },
                    leadingIcon = {
                        Icon(sportIcon(value), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
        if (sport.isEmpty()) {
            Text(
                text = "先选一个类型，记录里才知道这是瑜伽、骑行还是跑步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
            isError = duration.isNotEmpty() && !durationValid
        )
        if (duration.isNotEmpty() && !durationValid) {
            Text(
                "请输入 ${SportFields.DURATION_RANGE.first}–${SportFields.DURATION_RANGE.last} 之间的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DURATION_PRESETS.forEach { preset ->
                AssistChip(
                    onClick = { duration = preset.toString() },
                    label = { Text("$preset") }
                )
            }
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
