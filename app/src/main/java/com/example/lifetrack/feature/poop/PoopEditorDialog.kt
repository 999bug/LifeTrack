package com.example.lifetrack.feature.poop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.feature.record.ChoiceRow
import com.example.lifetrack.feature.record.RecordEditorDialog
import com.example.lifetrack.feature.record.TriStateRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

/**
 * 排便详情编辑器：通用事件壳 + 本类型专属的六个详情字段 + 用时。
 *
 * 用时按「两次进入」的语义实现（见 [PoopFields]）：
 * - 新增记录只写开始时间（= 事件时刻），保存后处于「进行中」；
 * - 再次打开已有记录时，顶部给出「结束记录（现在）」，点一下就把当前时间写成结束时间，
 *   用时自动算出；
 * - 超过 [PoopFields.MAX_DURATION_MINUTES] 分钟判为本次用时无效（记录本身保留），
 *   此时可以手动填写实际用时；手填值时不再写结束时间。
 *
 * 对外签名保持不变，导航与 ViewModel 无需感知编辑器变化。
 */
@Composable
fun PoopEditorDialog(
    record: PoopRecordRelation?,
    onDismiss: () -> Unit,
    onSave: (PoopRecordDraft) -> Unit
) {
    var bristol by remember(record) { mutableStateOf(record?.detail?.bristolType) }
    var amount by remember(record) { mutableStateOf(record?.detail?.amountCode) }
    var effort by remember(record) { mutableStateOf(record?.detail?.effortCode) }
    var urgency by remember(record) { mutableStateOf(record?.detail?.urgency) }
    var abdominal by remember(record) { mutableStateOf(record?.detail?.abdominalDiscomfort) }
    var incomplete by remember(record) { mutableStateOf(record?.detail?.incompleteEvacuation) }
    var showGuide by remember { mutableStateOf(false) }

    val startMillis = record?.event?.occurredAtEpochMillis
    val zone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    // 只有「还没判过用时」的记录才是进行中；已有用时（自动或手填）的记录直接展示结果。
    var endedAt by remember(record) { mutableStateOf(record?.detail?.endedAtEpochMillis) }
    var manual by remember(record) {
        // 自动算出的用时由结束时间代表，不预填进手填框，否则一保存就会把手填值当成本意。
        val fromManual = record?.detail?.endedAtEpochMillis == null
        mutableStateOf(
            if (fromManual) record?.detail?.durationMinutes?.toString().orEmpty() else ""
        )
    }
    val nowMillis = remember(startMillis) { System.currentTimeMillis() }
    val elapsed = if (startMillis == null) 0 else PoopFields.measuredMinutes(startMillis, nowMillis)
    val measured = if (startMillis == null || endedAt == null) {
        null
    } else {
        PoopFields.measuredMinutes(startMillis, endedAt!!)
    }
    val manualValid = manual.isEmpty() || PoopFields.isValidManualDuration(manual)
    val measuredValid = measured != null && PoopFields.isMeasuredValid(measured)

    RecordEditorDialog(
        title = if (record == null) "详细记录" else "编辑排便记录",
        resetKey = record,
        initialMillis = record?.event?.occurredAtEpochMillis,
        initialZoneId = record?.event?.zoneId,
        initialNote = record?.event?.note.orEmpty(),
        saveEnabled = manualValid,
        onDismiss = onDismiss,
        onSave = { value ->
            val timing = PoopFields.resolve(
                startEpochMillis = value.occurredAtEpochMillis,
                endedAtEpochMillis = endedAt,
                manualMinutes = manual
            )
            onSave(
                PoopRecordDraft(
                    occurredAtEpochMillis = value.occurredAtEpochMillis,
                    zoneId = value.zoneId,
                    bristolType = bristol,
                    amountCode = amount,
                    effortCode = effort,
                    urgency = urgency,
                    abdominalDiscomfort = abdominal,
                    incompleteEvacuation = incomplete,
                    note = value.note,
                    endedAtEpochMillis = timing.endedAtEpochMillis,
                    durationMinutes = timing.durationMinutes
                )
            )
        }
    ) {
        PoopTimingSection(
            hasStart = startMillis != null,
            startLabel = startMillis?.let { formatTime(it, zone) },
            endLabel = endedAt?.let { formatTime(it, zone) },
            elapsed = elapsed,
            measured = measured,
            measuredValid = measuredValid,
            manual = manual,
            manualValid = manualValid,
            onEnd = {
                endedAt = System.currentTimeMillis()
                manual = ""
            },
            onReTime = { endedAt = null },
            onManualChange = { input ->
                manual = input.filter(Char::isDigit).take(3)
                if (manual.isNotEmpty()) endedAt = null
            }
        )

        BristolTypePicker(
            selected = bristol,
            onSelect = { bristol = it },
            onShowGuide = { showGuide = true }
        )
        Text("排便量（可选）", style = MaterialTheme.typography.titleSmall)
        ChoiceRow(
            labels = listOf("少量", "适中", "大量"),
            selected = amount,
            values = listOf(1, 2, 3),
            onSelect = { amount = it }
        )
        Text("费力程度（可选）", style = MaterialTheme.typography.titleSmall)
        ChoiceRow(
            labels = listOf("不费力", "轻微", "明显"),
            selected = effort,
            values = listOf(1, 2, 3),
            onSelect = { effort = it }
        )
        TriStateRow("便意急迫感", urgency) { urgency = it }
        TriStateRow("腹部不适", abdominal) { abdominal = it }
        TriStateRow("排不净感", incomplete) { incomplete = it }
    }

    if (showGuide) {
        BristolGuideDialog(onDismiss = { showGuide = false })
    }
}

/**
 * 用时区：进行中 / 已结束 / 超阈值三种状态只出现一种，避免同时给出互相矛盾的提示。
 * 手填框常驻，随时可以绕过自动计时。
 */
@Composable
private fun PoopTimingSection(
    hasStart: Boolean,
    startLabel: String?,
    endLabel: String?,
    elapsed: Int,
    measured: Int?,
    measuredValid: Boolean,
    manual: String,
    manualValid: Boolean,
    onEnd: () -> Unit,
    onReTime: () -> Unit,
    onManualChange: (String) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("用时（可选）", style = MaterialTheme.typography.titleSmall)

        when {
            !hasStart -> Text(
                text = "保存后开始计时：这次只记开始时间，再次进入这条记录时可以记录结束时间，自动算出用时。",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )

            measured == null && manual.isEmpty() && elapsed > PoopFields.MAX_DURATION_MINUTES -> Text(
                text = "这次记录开始于 $startLabel，已超过 ${PoopFields.MAX_DURATION_MINUTES} 分钟，" +
                    "自动计时不再有效；如果实际用时很短，请在下面手动填写。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            measured == null && manual.isEmpty() -> {
                Text(
                    text = "进行中 · 开始于 $startLabel。结束时点下面的「结束记录」，把现在记成结束时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                OutlinedButton(onClick = onEnd) { Text("结束记录（现在）") }
            }

            measured != null && !measuredValid -> {
                Text(
                    text = "结束时间距开始已 $measured 分钟，超过 ${PoopFields.MAX_DURATION_MINUTES} 分钟，" +
                        "本次用时判为无效（记录本身会保留）；可手动填写实际用时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onReTime) { Text("清除结束时间") }
            }

            measured != null -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "已用时 $measured 分钟 · 结束于 $endLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReTime) { Text("重新计时") }
            }
        }

        OutlinedTextField(
            value = manual,
            onValueChange = onManualChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手动填写用时（分钟）") },
            supportingText = {
                Text(
                    "留空表示不手动填写；可填 ${PoopFields.DURATION_RANGE.first}–" +
                        "${PoopFields.DURATION_RANGE.last} 分钟"
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !manualValid
        )
        if (!manualValid) {
            Text(
                text = "请输入 ${PoopFields.DURATION_RANGE.first}–${PoopFields.DURATION_RANGE.last} 之间的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)
