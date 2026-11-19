package com.example.lifetrack.feature.menstruation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.ui.CycleBand
import com.example.lifetrack.core.ui.CycleRing
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.core.ui.cycleVisualColors
import com.example.lifetrack.feature.statistics.CardShape
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 经期页使用的模块色派生色；深浅主题各取一次，避免各组件自行拼色。 */
internal data class PeriodPalette(
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val accentBorder: Color,
    val surface: Color,
    val muted: Color,
    val danger: Color
)

@Composable
internal fun rememberPeriodPalette(): PeriodPalette {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val accent = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    return PeriodPalette(
        accent = accent,
        onAccent = onModuleColor(accent),
        accentContainer = accent.copy(alpha = if (isDark) 0.18f else 0.10f),
        accentBorder = accent.copy(alpha = if (isDark) 0.38f else 0.30f),
        surface = colorScheme.surface,
        muted = colorScheme.onSurfaceVariant,
        danger = colorScheme.error
    )
}

/**
 * 经期页首屏的三段视觉组件（界面定稿 v2 · 批 2）：
 * [PeriodPhaseCard] 92dp 周期环、[PeriodCycleBandCard] 28 段连续色带、
 * [PeriodPrimaryActions] 单一 62dp 主行动 + 设为开始日 / 改结束日期、[PeriodInsightSection] 洞察折叠。
 *
 * 这里只负责呈现，状态与写入全部由 [PeriodScreen] / [com.example.lifetrack.feature.home.MainViewModel] 提供。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodPhaseCard(
    dayIndex: Int,
    cycleLength: Int,
    periodLength: Int,
    recordedDays: Int,
    badge: String,
    title: String,
    description: String,
    summary: String,
    modifier: Modifier = Modifier
) {
    val colors = cycleVisualColors()
    val palette = rememberPeriodPalette()
    val stacked = LocalDensity.current.fontScale > 1.3f
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.accentContainer)
            .border(1.dp, palette.accentBorder, shape)
            .padding(horizontal = 18.dp, vertical = 17.dp)
            .semantics { this.contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bloodtype,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent
                    )
                }
                Text(
                    text = "周期 $cycleLength 天 · 经期 $periodLength 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Bloodtype,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "周期 $cycleLength 天 · 经期 $periodLength 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted
                )
            }
        }
        if (stacked) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CycleRing(
                    dayIndex = dayIndex,
                    cycleLength = cycleLength,
                    periodLength = periodLength,
                    size = 92.dp,
                    showCaption = true,
                    colors = colors,
                    contentDescription = "周期第 $dayIndex 天，共 $cycleLength 天，经期 $periodLength 天，已记录 $recordedDays 天"
                )
            }
            PeriodPhaseText(title, description, palette.accent)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CycleRing(
                    dayIndex = dayIndex,
                    cycleLength = cycleLength,
                    periodLength = periodLength,
                    size = 92.dp,
                    showCaption = true,
                    colors = colors,
                    contentDescription = "周期第 $dayIndex 天，共 $cycleLength 天，经期 $periodLength 天，已记录 $recordedDays 天"
                )
                Column(modifier = Modifier.weight(1f)) {
                    PeriodPhaseText(title, description, palette.accent)
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PeriodLegendItem("已记录", colors.period, solid = true)
            PeriodLegendItem("预计日期", colors.period, solid = false)
            PeriodLegendItem("今天", palette.accent, solid = true)
        }
    }
}

@Composable
private fun PeriodPhaseText(title: String, description: String, periodColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 5.dp)
    )
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PeriodLegendItem(label: String, color: Color, solid: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (solid) color else color.copy(alpha = 0.3f))
        )
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 整周期视图：连续色带 + 日期范围 + 三段轴标签，状态不只用颜色表达。 */
@Composable
fun PeriodCycleBandCard(
    dayIndex: Int,
    cycleLength: Int,
    periodLength: Int,
    recordedDays: Int,
    startDate: LocalDate?,
    modifier: Modifier = Modifier
) {
    val colors = cycleVisualColors()
    val palette = rememberPeriodPalette()
    val shape = RoundedCornerShape(16.dp)
    val endDate = startDate?.plusDays((cycleLength - 1).toLong())
    val range = if (startDate != null && endDate != null) {
        "${cn(startDate)} – ${cn(endDate)} · $cycleLength 天"
    } else {
        "尚无完整周期记录 · 按 $cycleLength 天显示"
    }
    val bandDescription = "整周期视图，共 $cycleLength 天，经期 $periodLength 天，已记录 $recordedDays 天，今天是周期第 $dayIndex 天。$range"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.accentBorder.copy(alpha = 0.55f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (LocalDensity.current.fontScale > 1.3f) {
            // 200% 字体下标题与日期范围并排会把两行都挤断，改成上下两行。
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "整周期视图", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = range,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "整周期视图",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = range,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        CycleBand(
            dayIndex = dayIndex,
            cycleLength = cycleLength,
            periodLength = periodLength,
            recordedDays = recordedDays,
            colors = colors,
            contentDescription = bandDescription,
            modifier = Modifier.padding(top = 10.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("本次经期", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("周期中段", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("预计下次", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 次级动作共用的窄内边距：默认 24dp 在 360dp 屏上会把 5 个汉字挤到换行。 */
private val SecondaryActionPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)

/**
 * 单一主行动 + 两个纠错动作。
 *
 * 纠错只在「选中的那天确实是最近一段经期里的日子」时出现（[showSecondary]）：
 * 起止日期都是由记录派生的，点了不是经期的日子去改，只会改出一段对不上的数据。
 * 所以不提供常驻按钮，也不做「先进入选择模式再点日期」的两步操作——
 * 点哪天就是哪天，选中经期日之后才有这两个动作。
 */
@Composable
fun PeriodPrimaryActions(
    primaryLabel: String,
    primaryIsCheck: Boolean,
    onPrimary: () -> Unit,
    showSecondary: Boolean,
    canSetStart: Boolean,
    onSetStart: () -> Unit,
    canEditEnd: Boolean,
    onEditEnd: () -> Unit,
    hint: String?,
    modifier: Modifier = Modifier
) {
    val palette = rememberPeriodPalette()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .semantics { contentDescription = primaryLabel },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent
            )
        ) {
            if (primaryIsCheck) {
                PeriodCheck(color = palette.onAccent, size = 20.dp)
            } else {
                Icon(
                    imageVector = LifeTrackTokens.ModuleIcons.of(MenstruationFields.TYPE_CODE),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(9.dp))
            Text(primaryLabel, style = MaterialTheme.typography.titleSmall)
        }
        if (showSecondary) {
            // 字体放大到 1.3 倍以上时两键并排放不下五个汉字，改成上下两行，避免文字被裁。
            if (LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SecondaryActionButton(
                        label = "设为开始日",
                        onClick = onSetStart,
                        enabled = canSetStart,
                        description = "把选中的这一天设为经期开始日",
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryActionButton(
                        label = "改结束日期",
                        onClick = onEditEnd,
                        enabled = canEditEnd,
                        description = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryActionButton(
                        label = "设为开始日",
                        onClick = onSetStart,
                        enabled = canSetStart,
                        description = "把选中的这一天设为经期开始日",
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryActionButton(
                        label = "改结束日期",
                        onClick = onEditEnd,
                        enabled = canEditEnd,
                        description = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = palette.accent,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 次级动作按钮：窄内边距 + 单行，保证两个按钮等分一行时不裁字。 */
@Composable
private fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    description: String?,
    modifier: Modifier = Modifier
) {
    val palette = rememberPeriodPalette()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, palette.accent.copy(alpha = if (enabled) 0.5f else 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = palette.accent,
            disabledContentColor = palette.muted.copy(alpha = 0.55f)
        ),
        contentPadding = SecondaryActionPadding,
        modifier = modifier
            .heightIn(min = 44.dp)
            .semantics { if (description != null) contentDescription = description }
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** 周期洞察折叠：简报 / 趋势 / 设置 / 补记工具默认收起，首屏留给环、色带、日历与主行动。 */
@Composable
fun PeriodInsightSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = rememberPeriodPalette()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.accentBorder.copy(alpha = 0.55f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .clickable { onToggle() }
                .semantics {
                    stateDescription = if (expanded) "已展开" else "已收起"
                    contentDescription = "周期洞察"
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "周期洞察",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = palette.accent
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

private fun cn(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

internal fun cycleDayIndex(start: LocalDate?, today: LocalDate, cycleLength: Int): Int =
    if (start == null) 1 else (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, cycleLength)
