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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PeriodDayFormModel
import com.example.lifetrack.core.model.PeriodField
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.statistics.CardShape

/**
 * 经期逐行填写区（界面定稿 v2 · 批 2）。
 *
 * 一行一字段，顺序固定为 流量 / 痛经 / 症状 / 心情 / 分泌物 / 日记；点行展开、
 * 同一时刻只展开一行，选中即保存、再点可取消。与旧版的两处关键差别：
 * 1. 5 类字段各用一套**形状不同**的图示（液滴 / 脉冲波纹 / 点阵 / 表情 / 横条），
 *    不再共用同一组递增竖条；选项与已填值走同一个 [PeriodFieldGlyph]。
 * 2. 顶部常驻「图形化值预览 + 已填 N / 6」：不展开也能看出今天记了什么、还差几项。
 *
 * 本 composable 不持有可变状态：全部读写都经 [PeriodDayFormModel] 与回调。
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
        FormProgress(model = model, moduleColor = moduleColor)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CardShape)
        ) {
            PeriodField.entries.forEachIndexed { index, field ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
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

/** 收起态也能看到今天记了什么：6 个字段各一枚图形化值预览，进度由选中日标题统一播报。 */
@Composable
private fun FormProgress(model: PeriodDayFormModel, moduleColor: Color) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    val glyphSize = if (stacked) 22.dp else 26.dp
    val cell = glyphSize + 8.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PeriodField.entries.forEach { field ->
            val isFilled = model.isFilled(field)
            val color = if (isFilled) moduleColor else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(cell)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (isFilled) {
                            moduleColor.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .semantics { contentDescription = "${fieldLabel(field)}：${if (isFilled) "已填" else "未填"}" },
                contentAlignment = Alignment.Center
            ) {
                PeriodFieldGlyph(
                    field = field,
                    level = fieldLevel(model, field),
                    symptomLevel = model.symptoms.size.coerceAtMost(4),
                    color = color.copy(alpha = if (isFilled) 1f else 0.45f),
                    size = glyphSize
                )
            }
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
    val level = fieldLevel(model, field)
    val rowDescription = buildString {
        append(fieldLabel(field))
        append("，")
        append(if (filled) fieldValueText(model, field) else "未填写")
        append("，")
        append(if (open) "已展开" else "已收起")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onToggleRow(field) }
                .semantics {
                    stateDescription = if (open) "已展开" else "已收起"
                    contentDescription = rowDescription
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                PeriodFieldGlyph(
                    field = field,
                    level = level,
                    symptomLevel = model.symptoms.size.coerceAtMost(4),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp
                )
            }
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

/** 已填值的图形化预览：与选项共用同一套图示，不靠颜色单独表达。 */
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
            model.symptoms.take(3).forEach { code ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PeriodSymptomGrid(
                        level = model.symptoms.size.coerceAtMost(4),
                        total = 4,
                        color = moduleColor,
                        size = 14.dp
                    )
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
            PeriodFieldGlyph(
                field = field,
                level = fieldLevel(model, field),
                color = moduleColor,
                size = 22.dp
            )
            Text(
                text = fieldValueText(model, field),
                style = MaterialTheme.typography.bodySmall,
                color = moduleColor
            )
        }
    }
}

/** 选项改成 56dp 图形方块：图示 26dp + 文字，触摸目标 ≥44dp，选中态同时用边框和底色。 */
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
            fieldValues(field).forEachIndexed { index, value ->
                val selected = value == selectedValue
                val label = optionLabel(field, value)
                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                        .clip(shape)
                        .background(
                            if (selected) {
                                moduleColor.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        )
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) moduleColor else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape
                        )
                        .clickable { onSelectOption(field, value) }
                        .semantics {
                            role = Role.Button
                            stateDescription = if (selected) "已选中" else "未选中"
                            contentDescription = "${fieldLabel(field)}：$label"
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        PeriodFieldGlyph(
                            field = field,
                            level = index + 1,
                            color = if (selected) moduleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 26.dp
                        )
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 1-based 档位：0 = 未填 / 无。症状按已选数量映射到 4 档点阵。 */
private fun fieldLevel(model: PeriodDayFormModel, field: PeriodField): Int = when (field) {
    PeriodField.SYMPTOMS -> model.symptoms.size.coerceIn(0, 4)
    PeriodField.DIARY -> if (model.diary.isEmpty()) 0 else 1
    else -> fieldValues(field).indexOf(currentValue(model, field)).let { if (it < 0) 0 else it + 1 }
}

private fun fieldValueText(model: PeriodDayFormModel, field: PeriodField): String = when (field) {
    PeriodField.FLOW -> MenstruationFields.flowLabel(model.flow)
    PeriodField.PAIN -> MenstruationFields.painLabel(model.pain)
    PeriodField.MOOD -> MenstruationFields.moodLabel(model.mood)
    PeriodField.DISCHARGE -> MenstruationFields.dischargeLabel(model.discharge)
    PeriodField.SYMPTOMS -> "${model.symptoms.size} 项"
    PeriodField.DIARY -> truncateDiary(model.diary)
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
