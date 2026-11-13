package com.example.lifetrack.feature.menstruation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/**
 * 经期页首屏的三段视觉组件（界面定稿 v2 · 批 2）：
 * [PeriodPhaseCard] 92dp 周期环、[PeriodCycleBandCard] 28 段连续色带、
 * [PeriodPrimaryActions] 单一 62dp 主行动 + 撤销 / 改开始日期、[PeriodInsightSection] 洞察折叠。
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
    val stacked = LocalDensity.current.fontScale > 1.3f
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(16.dp)
            .semantics { this.contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            PeriodPhaseText(badge, title, description, colors.period)
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
                    PeriodPhaseText(badge, title, description, colors.period)
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PeriodLegendItem("经期实色弧", colors.period, solid = true)
            PeriodLegendItem("预计虚线弧", colors.period, solid = false)
            PeriodLegendItem("今天定位点", colors.primary, solid = true)
        }
    }
}

@Composable
private fun PeriodPhaseText(badge: String, title: String, description: String, periodColor: Color) {
    Text(text = badge, style = MaterialTheme.typography.labelSmall, color = periodColor)
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
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
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
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

/**
 * 单一主行动 + 两个次级动作。旧版的 FlowRow 双按钮里有一个 disabled 灰块，
 * 与另一键错位（深色模式尤其明显）；现在只保留一个状态相关的主按钮。
 */
@Composable
fun PeriodPrimaryActions(
    primaryLabel: String,
    primaryIsCheck: Boolean,
    onPrimary: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onChangeStart: () -> Unit,
    pickStartMode: Boolean,
    hint: String?,
    modifier: Modifier = Modifier
) {
    val moduleColor = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val onModule = onModuleColor(moduleColor)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .semantics { contentDescription = primaryLabel },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = moduleColor,
                contentColor = onModule
            )
        ) {
            if (primaryIsCheck) {
                PeriodCheck(color = onModule, size = 20.dp)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            ) { Text("撤销上一步", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(
                onClick = onChangeStart,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .semantics { stateDescription = if (pickStartMode) "正在选择开始日期" else "未选择" },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (pickStartMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                Text(
                    text = if (pickStartMode) "取消选择" else "改开始日期",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = moduleColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
