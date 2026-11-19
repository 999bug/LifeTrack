@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.lifetrack.feature.intimacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

private val intimacyDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)

/**
 * 亲密关系编辑器：梅紫全屏私密记录页。
 *
 * 对外签名、措施字段、旧版字段保留策略与隐私文案均保持不变。页面只调整信息层级，
 * 日期仍由经期日历决定，备注不会进入首页或历史摘要。
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
    val largeFont = LocalDensity.current.fontScale > 1.3f

    val title = if (record == null) "第 ${ordinal.coerceAtLeast(1)} 次亲密记录" else "编辑亲密记录"
    val timeLabel = String.format(Locale.CHINA, "%02d:%02d", hour, minute)
    val methodLabel = method?.let(IntimacyFields::methodLabel) ?: "未选择措施"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                IntimacyEditorHeader(
                    title = title,
                    accent = accent,
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = LifeTrackTokens.Spacing.page),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IntimacyHeroCard(
                        timeLabel = timeLabel,
                        methodLabel = methodLabel,
                        accent = accent
                    )

                    IntimacySectionCard(
                        number = "01",
                        title = "日期与时间",
                        description = "日期沿用当前选择日；这里只调整本次记录的时间。",
                        accent = accent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = date.format(intimacyDateFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        TimeWheelPicker(
                            hour = hour,
                            minute = minute,
                            accent = accent,
                            onHourChange = { hour = it },
                            onMinuteChange = { minute = it }
                        )
                    }

                    IntimacySectionCard(
                        number = "02",
                        title = "防护方式",
                        description = "单选，可不选。只记录措施本身，不记录伴侣或任何可识别身份的信息。",
                        accent = accent
                    ) {
                        IntimacyMethodGrid(
                            selected = method,
                            lastMethod = lastMethod,
                            accent = accent,
                            onSelect = { picked -> method = if (method == picked) null else picked }
                        )
                        Surface(
                            color = accent.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "当前选择",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = methodLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accent,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    IntimacySectionCard(
                        number = "03",
                        title = "备注",
                        description = "备注不会进入首页、历史摘要或读屏汇总。",
                        accent = accent
                    ) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("给这次记录留一句话") },
                            placeholder = { Text("只记录自己需要的信息，请勿填写他人身份信息。") },
                            minLines = 3,
                            supportingText = { Text("${note.length} / 500") }
                        )
                        Text(
                            text = "建议在「设置 → 安全」开启应用锁；本月记录只保存在本机。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                }

                IntimacySaveBar(
                    summary = "$timeLabel · $methodLabel",
                    isEditing = record != null,
                    largeFont = largeFont,
                    onSave = {
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
                )
            }
        }
    }
}

@Composable
private fun IntimacyEditorHeader(
    title: String,
    accent: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "亲密关系 · 私密记录",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("仅本机", style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}

@Composable
private fun IntimacyHeroCard(
    timeLabel: String,
    methodLabel: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("本次记录", style = MaterialTheme.typography.labelMedium, color = accent)
                Text(timeLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "$methodLabel · 详情只在本机保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntimacySectionCard(
    number: String,
    title: String,
    description: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

/**
 * 措施宫格：常规字号 5 列，大字号 2 列并允许两行文案，避免选项文字被压扁或截断。
 */
@Composable
private fun IntimacyMethodGrid(
    selected: String?,
    lastMethod: String?,
    accent: Color,
    onSelect: (String) -> Unit
) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val columns = if (largeFont) 2 else 5
    IntimacyFields.METHOD_OPTIONS.chunked(columns).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row.forEach { (code, label) ->
                MethodTile(
                    code = code,
                    label = label,
                    selected = selected == code,
                    isLast = lastMethod == code && selected != code,
                    accent = accent,
                    largeFont = largeFont,
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
    largeFont: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF281920) else Color.White
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp)
            )
            .background(if (selected) accent.copy(alpha = 0.09f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(code) })
            .semantics {
                contentDescription = label
                stateDescription = when {
                    selected -> "已选中"
                    isLast -> "上次使用"
                    else -> "未选中"
                }
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (largeFont) 46.dp else 48.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = if (selected) 0f else 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IntimacyMethodIcon(
                    method = code,
                    color = if (selected) onAccent else accent,
                    size = if (largeFont) 24.dp else 25.dp
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    PeriodCheck(color = accent, size = 12.dp)
                }
            }
            if (isLast) {
                Text(
                    text = "上次",
                    style = MaterialTheme.typography.labelSmall,
                    color = onAccent,
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
            style = MaterialTheme.typography.labelSmall,
            maxLines = if (largeFont) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IntimacySaveBar(
    summary: String,
    isEditing: Boolean,
    largeFont: Boolean,
    onSave: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp
    ) {
        if (largeFont) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IntimacySaveButton(isEditing, onSave, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IntimacySaveButton(isEditing, onSave)
            }
        }
    }
}

@Composable
private fun IntimacySaveButton(
    isEditing: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onSave,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (isEditing) "保存修改" else "保存私密记录",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
