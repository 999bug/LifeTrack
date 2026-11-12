package com.example.lifetrack.feature.menstruation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.record.ChoiceChipRow
import com.example.lifetrack.feature.record.RecordEditorDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 编辑器里「未记录」的占位值，只用于 chip 选中态，绝不写入详情 map。 */
private const val NO_ENTRY = ""

/**
 * 月经编辑器：首页时间轴 / 历史页 / 记录列表点编辑时走这里，复用通用事件壳。
 *
 * 五项详情均可空，全不填时整条记录不带 detail，与恢复路径「无详情仍合法」一致；
 * 日记就是壳层的备注框（[RecordEditorDialog] 的 noteLabel 改成「日记」），数据落在 `note`。
 * 与子应用 [PeriodDayForm] 共用 [MenstruationFields] 枚举 / validator 与 [SymptomChips]，不写两份白名单。
 * [initialDate] 仅在没有可编辑记录时用于预设日期（例如从某个日期上下文新增）。
 */
@Composable
fun MenstruationEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit,
    initialDate: LocalDate? = null
) {
    val initial = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
    var flow by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.FLOW]) }
    var pain by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.PAIN]) }
    var mood by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.MOOD]) }
    var discharge by remember(record) { mutableStateOf<String?>(initial[MenstruationFields.DISCHARGE]) }
    var symptoms by remember(record) {
        mutableStateOf(MenstruationFields.decodeSymptoms(initial[MenstruationFields.SYMPTOMS].orEmpty()))
    }

    val initialMillis = record?.event?.occurredAtEpochMillis ?: initialDate?.let { date ->
        val zone = runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
        date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }

    RecordEditorDialog(
        title = if (record == null) "记录月经" else "编辑月经记录",
        resetKey = record,
        initialMillis = initialMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        noteLabel = "日记（可选）",
        onDismiss = onDismiss,
        onSave = { value ->
            val fields = buildMap {
                flow?.let { put(MenstruationFields.FLOW, it) }
                pain?.let { put(MenstruationFields.PAIN, it) }
                if (symptoms.isNotEmpty()) {
                    put(MenstruationFields.SYMPTOMS, MenstruationFields.encodeSymptoms(symptoms))
                }
                mood?.let { put(MenstruationFields.MOOD, it) }
                discharge?.let { put(MenstruationFields.DISCHARGE, it) }
            }
            onSave(
                RecordDraft(
                    typeCode = MenstruationFields.TYPE_CODE,
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    note = value.note,
                    detail = if (fields.isEmpty()) {
                        null
                    } else {
                        RecordDetail.Generic(MenstruationFields.TYPE_CODE, fields)
                    }
                )
            )
        }
    ) {
        Text("流量", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(
            values = listOf(NO_ENTRY) + MenstruationFields.FLOW_VALUES,
            selected = flow ?: NO_ENTRY,
            label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.flowLabel(it) },
            onSelect = { flow = it.takeIf { selected -> selected != NO_ENTRY } }
        )

        Text("痛经", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(
            values = listOf(NO_ENTRY) + MenstruationFields.PAIN_VALUES,
            selected = pain ?: NO_ENTRY,
            label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.painLabel(it) },
            onSelect = { pain = it.takeIf { selected -> selected != NO_ENTRY } }
        )

        Text("症状（最多 ${MenstruationFields.MAX_SYMPTOMS} 项）", style = MaterialTheme.typography.titleSmall)
        SymptomChips(
            selected = symptoms,
            onToggle = { code ->
                symptoms = when {
                    code in symptoms -> symptoms - code
                    symptoms.size < MenstruationFields.MAX_SYMPTOMS -> symptoms + code
                    else -> symptoms
                }
            }
        )

        Text("心情", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(
            values = listOf(NO_ENTRY) + MenstruationFields.MOOD_VALUES,
            selected = mood ?: NO_ENTRY,
            label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.moodLabel(it) },
            onSelect = { mood = it.takeIf { selected -> selected != NO_ENTRY } }
        )

        Text("分泌物", style = MaterialTheme.typography.titleSmall)
        ChoiceChipRow(
            values = listOf(NO_ENTRY) + MenstruationFields.DISCHARGE_VALUES,
            selected = discharge ?: NO_ENTRY,
            label = { if (it == NO_ENTRY) "未记录" else MenstruationFields.dischargeLabel(it) },
            onSelect = { discharge = it.takeIf { selected -> selected != NO_ENTRY } }
        )

        Text(
            "五项均可不填。日记仅存为本条记录的备注，请勿记录他人身份信息；建议在「设置 → 安全」开启应用锁。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}