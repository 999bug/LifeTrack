package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PeriodDayFormModel
import com.example.lifetrack.core.model.PeriodField
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.statistics.CardShape

/**
 * 经期子应用的逐行填写区（设计 §5.4 ④ / §5.8）。一行一字段，顺序固定为
 * 流量 / 痛经 / 症状 / 心情 / 分泌物 / 日记；点行展开、同一时刻只展开一行，
 * 选项与已填值共用 [PeriodDayIcons] 的同一套自绘图示。选中即保存、再点可取消，
 * 由调用方把每次变更落库；未来日期整块只读，不给可填控件、不编造记录。
 *
 * 本 composable 不持有可变状态：全部读写都经 [PeriodDayFormModel] 与回调，
 * 滚动位置由外层 `verticalScroll` 的 `rememberScrollState` 保持，点选项不会跳回顶部。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodDayForm(
    model: PeriodDayFormModel,
    predicted: Boolean,
    onToggleRow: (PeriodField) -> Unit,
    onSelectOption: (PeriodField, String) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onDiaryChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moduleColor = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)

    if (model.readOnly) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (predicted) "预计经期 · 不是记录" else "未来的日期",
                style = MaterialTheme.typography.titleSmall,
                color = moduleColor
            )
            Text(
                text = if (predicted) {
                    "这是按已记录周期推算的日期，不是已记录的数据。"
                } else {
                    "这一天还没有到来。"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "未来日期不能填写，可在日历上点过去任意一天补记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CardShape)
        ) {
            PeriodField.entries.forEachIndexed { index, field ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PeriodFieldRow(
                    field = field,
                    model = model,
                    moduleColor = moduleColor,
                    onToggleRow = onToggleRow,
                    onSelectOption = onSelectOption,
                    onToggleSymptom = onToggleSymptom,
                    onDiaryChange = onDiaryChange
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "选择后即时保存，同一天只记一条；不填也可以。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onDone) { Text("完成") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodFieldRow(
    field: PeriodField,
    model: PeriodDayFormModel,
    moduleColor: Color,
    onToggleRow: (PeriodField) -> Unit,
    onSelectOption: (PeriodField, String) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onDiaryChange: (String) -> Unit
) {
    val open = model.openRow == field
    val filled = model.isFilled(field)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onToggleRow(field) }
                .semantics { stateDescription = if (open) "已展开" else "已收起" }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowHeadIcon(field = field, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = fieldLabel(field), style = MaterialTheme.typography.bodyLarge)
                FieldValue(field = field, model = model, filled = filled, moduleColor = moduleColor)
            }
            Icon(
                imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (open) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                FieldOptions(
                    field = field,
                    model = model,
                    moduleColor = moduleColor,
                    onSelectOption = onSelectOption,
                    onToggleSymptom = onToggleSymptom,
                    onDiaryChange = onDiaryChange
                )
            }
        }
    }
}

/** 行首功能图示：固定尺寸、不随字号拉伸，颜色用中性的 onSurfaceVariant。 */
@Composable
private fun RowHeadIcon(field: PeriodField, color: Color) {
    when (field) {
        PeriodField.FLOW -> PeriodLevelBars(MenstruationFields.FLOW_VALUES.size, MenstruationFields.FLOW_VALUES.size, color)
        PeriodField.PAIN -> PeriodLevelBars(MenstruationFields.PAIN_VALUES.size, MenstruationFields.PAIN_VALUES.size, color)
        PeriodField.DISCHARGE -> PeriodLevelBars(MenstruationFields.DISCHARGE_VALUES.size, MenstruationFields.DISCHARGE_VALUES.size, color)
        PeriodField.MOOD -> PeriodMoodFace(index = 1, color = color)
        PeriodField.SYMPTOMS -> PeriodSymptomDot(color = color)
        PeriodField.DIARY -> PeriodDiary(color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldValue(field: PeriodField, model: PeriodDayFormModel, filled: Boolean, moduleColor: Color) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!filled) {
        Text(text = guidance(field), style = MaterialTheme.typography.bodySmall, color = muted)
        return
    }
    when (field) {
        PeriodField.SYMPTOMS -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            model.symptoms.forEach { code ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PeriodSymptomDot(color = moduleColor, size = 8.dp)
                    Text(
                        text = MenstruationFields.symptomLabel(code),
                        style = MaterialTheme.typography.bodySmall,
                        color = moduleColor
                    )
                }
            }
        }
        PeriodField.DIARY -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PeriodSymptomDot(color = moduleColor, size = 8.dp)
            Text(
                text = truncateDiary(model.diary),
                style = MaterialTheme.typography.bodySmall,
                color = moduleColor
            )
        }
        else -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ValueGlyph(field = field, value = currentValue(model, field), color = moduleColor, size = 16.dp)
            Text(
                text = optionLabel(field, currentValue(model, field)),
                style = MaterialTheme.typography.bodySmall,
                color = moduleColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldOptions(
    field: PeriodField,
    model: PeriodDayFormModel,
    moduleColor: Color,
    onSelectOption: (PeriodField, String) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onDiaryChange: (String) -> Unit
) {
    when (field) {
        PeriodField.SYMPTOMS -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SymptomChips(selected = model.symptoms, onToggle = onToggleSymptom)
            if (model.symptoms.size >= MenstruationFields.MAX_SYMPTOMS) {
                Text(
                    text = "已选满 ${MenstruationFields.MAX_SYMPTOMS} 项，取消一项后可再选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        PeriodField.DIARY -> OutlinedTextField(
            value = model.diary,
            onValueChange = onDiaryChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("记录今天的身体感受、作息或想说的话") },
            supportingText = {
                Text("${model.diary.length} 字 · 仅存为本条记录的备注，请勿记录他人身份信息")
            }
        )
        else -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val selectedValue = currentValue(model, field)
            fieldValues(field).forEach { value ->
                val selected = value == selectedValue
                FilterChip(
                    selected = selected,
                    onClick = { onSelectOption(field, value) },
                    label = { Text(optionLabel(field, value)) },
                    leadingIcon = {
                        ValueGlyph(
                            field = field,
                            value = value,
                            color = if (selected) moduleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 16.dp
                        )
                    }
                )
            }
        }
    }
}

/** 已填值与选项共用的图示：等级格 / 简笔表情，保证「选的和显示的一样」。 */
@Composable
private fun ValueGlyph(field: PeriodField, value: String, color: Color, size: Dp) {
    when (field) {
        PeriodField.FLOW -> PeriodLevelBars(levelFilled(MenstruationFields.FLOW_VALUES, value), MenstruationFields.FLOW_VALUES.size, color, size = size)
        PeriodField.PAIN -> PeriodLevelBars(levelFilled(MenstruationFields.PAIN_VALUES, value), MenstruationFields.PAIN_VALUES.size, color, size = size)
        PeriodField.DISCHARGE -> PeriodLevelBars(levelFilled(MenstruationFields.DISCHARGE_VALUES, value), MenstruationFields.DISCHARGE_VALUES.size, color, size = size)
        PeriodField.MOOD -> PeriodMoodFace(MenstruationFields.MOOD_VALUES.indexOf(value).coerceAtLeast(0), color, size = size)
        PeriodField.SYMPTOMS -> PeriodSymptomDot(color, size = size)
        PeriodField.DIARY -> PeriodDiary(color, size = size)
    }
}

private fun levelFilled(values: List<String>, value: String): Int {
    val index = values.indexOf(value)
    return if (index < 0) 0 else index + 1
}

private fun currentValue(model: PeriodDayFormModel, field: PeriodField): String = when (field) {
    PeriodField.FLOW -> model.flow
    PeriodField.PAIN -> model.pain
    PeriodField.MOOD -> model.mood
    PeriodField.DISCHARGE -> model.discharge
    PeriodField.SYMPTOMS, PeriodField.DIARY -> ""
}

private fun fieldValues(field: PeriodField): List<String> = when (field) {
    PeriodField.FLOW -> MenstruationFields.FLOW_VALUES
    PeriodField.PAIN -> MenstruationFields.PAIN_VALUES
    PeriodField.MOOD -> MenstruationFields.MOOD_VALUES
    PeriodField.DISCHARGE -> MenstruationFields.DISCHARGE_VALUES
    PeriodField.SYMPTOMS, PeriodField.DIARY -> emptyList()
}

private fun fieldLabel(field: PeriodField): String = when (field) {
    PeriodField.FLOW -> "流量"
    PeriodField.PAIN -> "痛经"
    PeriodField.SYMPTOMS -> "症状"
    PeriodField.MOOD -> "心情"
    PeriodField.DISCHARGE -> "分泌物"
    PeriodField.DIARY -> "日记"
}

private fun guidance(field: PeriodField): String = when (field) {
    PeriodField.SYMPTOMS -> "选择身体感受，最多 ${MenstruationFields.MAX_SYMPTOMS} 项"
    PeriodField.DIARY -> "写点今天的感受与作息"
    else -> "未填写"
}

private fun optionLabel(field: PeriodField, value: String): String = when (field) {
    PeriodField.FLOW -> MenstruationFields.flowLabel(value)
    PeriodField.PAIN -> MenstruationFields.painLabel(value)
    PeriodField.MOOD -> MenstruationFields.moodLabel(value)
    PeriodField.DISCHARGE -> MenstruationFields.dischargeLabel(value)
    PeriodField.SYMPTOMS, PeriodField.DIARY -> value
}

private fun truncateDiary(text: String): String =
    if (text.length > 22) text.take(22) + "…" else text