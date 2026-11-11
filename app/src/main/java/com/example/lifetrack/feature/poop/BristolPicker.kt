package com.example.lifetrack.feature.poop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.ui.LifeTrackTokens

/**
 * 排便编辑器里的布里斯托形态选择器：七型图形一次看全，选中后补一句确认文案。
 *
 * 约定：
 * - 只负责「选 / 取消 / 跳过」这一个 [Int]? 值，不认识草稿类型，也不碰存储与备份；
 * - 「再点一次取消」沿用旧数字 chip 的行为，「不确定」卡把未记录显式化，默认即选中；
 * - 大字号（fontScale > 1.3）改 2 列并用 heightIn(min) 撑高，避免固定 dp 裁掉第二行文字；
 * - 每张卡用 selectable(RadioButton) 暴露选中态，图形对读屏隐藏，语义是一句完整描述。
 */
@Composable
fun BristolTypePicker(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onShowGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val columns = if (largeFont) 2 else 4
    val artColor = LifeTrackTokens.ModuleColors.of(DEFAULT_POOP_TYPE.code)
    val selectedArtColor = MaterialTheme.colorScheme.onPrimaryContainer
    val outline = MaterialTheme.colorScheme.outlineVariant
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (largeFont) {
            Text("布里斯托类型（可选）", style = MaterialTheme.typography.titleSmall)
            TextButton(
                onClick = onShowGuide,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("七型对照", style = MaterialTheme.typography.labelLarge) }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "布里斯托类型（可选）",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onShowGuide,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("七型对照", style = MaterialTheme.typography.labelLarge) }
            }
        }

        if (largeFont) {
            Text(
                "偏干端 → 偏稀端（按含水量）",
                style = MaterialTheme.typography.bodySmall,
                color = hintColor
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("偏干端", style = MaterialTheme.typography.bodySmall, color = hintColor)
                Box(Modifier.weight(1f).height(1.dp).background(outline))
                Text("含水量 →", style = MaterialTheme.typography.bodySmall, color = hintColor)
                Box(Modifier.weight(1f).height(1.dp).background(outline))
                Text("偏稀端", style = MaterialTheme.typography.bodySmall, color = hintColor)
            }
        }

        val cells: List<BristolType?> = buildList {
            addAll(BristolCatalog.all)
            add(null)
        }
        cells.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { type ->
                    if (type == null) {
                        SkipCard(
                            selected = selected == null,
                            onClick = { onSelect(null) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        BristolCard(
                            type = type,
                            selected = selected == type.number,
                            artColor = artColor,
                            selectedArtColor = selectedArtColor,
                            onClick = { onSelect(if (selected == type.number) null else type.number) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        SelectionNote(selected?.let { BristolCatalog.of(it) })
    }
}

@Composable
private fun BristolCard(
    type: BristolType,
    selected: Boolean,
    artColor: Color,
    selectedArtColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "${type.number} 型，${type.shortLabel}，${type.group}。${type.description}" +
                    (if (selected) "。已选中" else "")
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BristolArt(
                number = type.number,
                color = if (selected) selectedArtColor else artColor,
                creaseColor = background
            )
            if (selected) {
                SelectedCheck(Modifier.align(Alignment.TopEnd))
            }
        }
        Text(
            text = "${type.number} 型",
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
        Text(
            text = type.shortLabel,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SkipCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(background)
            .drawBehind {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash)
                )
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "不确定，先跳过，不记录布里斯托类型" +
                    (if (selected) "。已选中" else "")
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("不确定", style = MaterialTheme.typography.labelMedium, color = textColor)
        Text("本项留空", style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

@Composable
private fun SelectionNote(selected: BristolType?) {
    val isSet = selected != null
    val background = if (isSet) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val stripe = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val titleColor = if (isSet) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bodyColor = if (isSet) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .drawBehind {
                drawRect(color = stripe, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Text(
            text = selected?.let { "${it.number} 型 · ${it.shortLabel}" } ?: "未记录",
            style = MaterialTheme.typography.bodyMedium,
            color = titleColor
        )
        Text(
            text = selected?.let { "${it.group} · ${it.description}" }
                ?: "选一个最接近的形态，看不准可以跳过；再点一次已选的卡片也能取消。其余字段保持现状，在编辑器内向下滚动查看。",
            style = MaterialTheme.typography.bodySmall,
            color = bodyColor
        )
    }
}

@Composable
private fun SelectedCheck(modifier: Modifier = Modifier) {
    val circle = MaterialTheme.colorScheme.primary
    val mark = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier.size(16.dp)) {
        drawCircle(circle, radius = 7.5.dp.toPx())
        val path = Path().apply {
            moveTo(4.5f.dp.toPx(), 8f.dp.toPx())
            lineTo(7f.dp.toPx(), 10.5f.dp.toPx())
            lineTo(11.5f.dp.toPx(), 5.5f.dp.toPx())
        }
        drawPath(
            path,
            mark,
            style = Stroke(
                width = 1.6f.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
