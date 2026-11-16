package com.example.lifetrack.feature.intimacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.feature.menstruation.PeriodCheck
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)

/**
 * 亲密关系编辑器（重构版）：整屏「取消 / 标题 / 确定」+ 措施图标宫格 + 时:分滚轮。
 *
 * 与旧版的差别：
 * - 措施从 3 选 1 的「保护措施」换成 10 选 1 的图标宫格，并给最近一次用过的措施打「上次」角标；
 * - 时间不再走系统时间选择器，而是常驻的时 / 分滚轮，一进页面就能看到当前值；
 * - 旧版的「主观感受 / 时长」不再提供输入，但编辑老记录时会原样保留（不因重构丢数据）。
 *
 * 全屏 Dialog 而不是 AlertDialog：10 枚宫格 + 滚轮 + 备注在 AlertDialog 的宽度里会挤成两列，
 * 参考界面本身就是整屏。日期不在本页修改：选中日由经期子应用的日历决定，
 * 编辑老记录也保持原来的日期，只改时间。
 */
@Composable
fun IntimacyEditorDialog(
    record: BackupRecord?,
    onDismiss: () -> Unit,
    onSave: (RecordDraft) -> Unit,
    ordinal: Int = 1,
    defaultDate: LocalDate? = null,
    lastMethod: String? = null
) {
    val accent = LifeTrackTokens.ModuleColors.of(IntimacyFields.TYPE_CODE)
    val zone = remember(record) {
        runCatching { ZoneId.of(record?.event?.zoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    val initial = remember(record, defaultDate) {
        val millis = record?.event?.occurredAtEpochMillis
        if (millis != null) {
            Instant.ofEpochMilli(millis).atZone(zone)
        } else {
            val date = defaultDate ?: LocalDate.now(zone)
            val time = if (date == LocalDate.now(zone)) {
                LocalTime.now(zone).withSecond(0).withNano(0)
            } else {
                LocalTime.NOON
            }
            date.atTime(time).atZone(zone)
        }
    }
    val date = initial.toLocalDate()
    val legacyFields = remember(record) {
        ((record?.detail as? RecordDetail.Generic)?.fields.orEmpty())
            .filterKeys { it != IntimacyFields.METHOD }
    }
    var method by remember(record, defaultDate) {
        mutableStateOf((record?.detail as? RecordDetail.Generic)?.fields?.get(IntimacyFields.METHOD))
    }
    var hour by remember(record, defaultDate) { mutableIntStateOf(initial.hour) }
    var minute by remember(record, defaultDate) { mutableIntStateOf(initial.minute) }
    var note by remember(record) { mutableStateOf(record?.event?.note.orEmpty()) }

    val title = if (record == null) "第 ${ordinal.coerceAtLeast(1)} 次亲密记录" else "编辑亲密记录"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val millis = date.atTime(LocalTime.of(hour, minute))
                                .atZone(zone).toInstant().toEpochMilli()
                            val fields = buildMap {
                                putAll(legacyFields)
                                method?.let { put(IntimacyFields.METHOD, it) }
                            }
                            onSave(
                                RecordDraft(
                                    typeCode = IntimacyFields.TYPE_CODE,
                                    occurredAtEpochMillis = millis,
                                    zoneId = zone.id,
                                    note = note.trim(),
                                    detail = if (fields.isEmpty()) {
                                        null
                                    } else {
                                        RecordDetail.Generic(IntimacyFields.TYPE_CODE, fields)
                                    }
                                )
                            )
                        }
                    ) { Text("确定", color = accent) }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionTitle(text = "措施", accent = accent)
                    Text(
                        text = "单选，可不选。只记录措施本身，不记录伴侣或任何可识别身份的信息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IntimacyMethodGrid(
                        selected = method,
                        lastMethod = lastMethod,
                        accent = accent,
                        onSelect = { picked -> method = if (method == picked) null else picked }
                    )

                    Spacer(Modifier.size(4.dp))
                    SectionTitle(text = "时间", accent = accent)
                    Text(
                        text = date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TimeWheelPicker(
                        hour = hour,
                        minute = minute,
                        accent = accent,
                        onHourChange = { hour = it },
                        onMinuteChange = { minute = it }
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注（可选）") },
                        minLines = 2
                    )
                    Text(
                        text = "建议在「设置 → 安全」开启应用锁；本月记录只保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * 措施宫格：常驻 10 项，5 列 × 2 行；大字号降到 3 列，避免 5 个字被压成一横条。
 * 选中 = 实心圆 + 白色图示 + 对勾；最近一次用过的项在没有选中时打「上次」角标。
 */
@Composable
private fun IntimacyMethodGrid(
    selected: String?,
    lastMethod: String?,
    accent: Color,
    onSelect: (String) -> Unit
) {
    // 5 列在常规字号下正好两行；大字号下 5 个字会被压成一横条，降到 3 列。
    val columns = if (LocalDensity.current.fontScale > 1.3f) 3 else 5
    IntimacyFields.METHOD_OPTIONS.chunked(columns).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row.forEach { (code, label) ->
                MethodTile(
                    code = code,
                    label = label,
                    selected = selected == code,
                    isLast = lastMethod == code && selected != code,
                    accent = accent,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MethodTile(
    code: String,
    label: String,
    selected: Boolean,
    isLast: Boolean,
    accent: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(code) })
            .semantics {
                contentDescription = label
                stateDescription = when {
                    selected -> "已选中"
                    isLast -> "上次使用"
                    else -> "未选中"
                }
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = if (selected) 0f else 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IntimacyMethodIcon(
                    method = code,
                    color = if (selected) Color.White else accent,
                    size = 26.dp
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    PeriodCheck(color = accent, size = 12.dp)
                }
            }
            if (isLast) {
                Text(
                    text = "上次",
                    fontSize = 9.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = label,
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
