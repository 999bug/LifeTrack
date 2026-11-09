package com.example.lifetrack.feature.statistics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 时段分布：24 根逐小时柱，高亮现有「常见 2 小时时段」滑动窗口对应的两根柱。
 * 两者共用 StatsAggregator 算出的 hourlyCounts，因此指标卡和柱图永远指向同一组数据。
 */
@Composable
internal fun TimeOfDayCard(stats: PeriodStats, colors: StatsColors, modifier: Modifier = Modifier) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CardShape)
            .padding(16.dp)
    ) {
        // 大字号下左右两栏会挤成一行，改为上下两行
        if (stacked) {
            Text(
                text = "时段分布",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "共 ${stats.total} 条 · 按记录时区",
                style = MaterialTheme.typography.bodySmall,
                color = colors.label.copy(alpha = 0.7f)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "时段分布",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "共 ${stats.total} 条 · 按记录时区",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.label.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = stats.commonBand?.let {
                        "逐小时分布，共 ${stats.total} 次，最常见时段 $it"
                    } ?: "逐小时分布，暂无记录"
                }
        ) {
            HourlyChart(stats = stats, colors = colors)
            if (!stats.hasData) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "这个周期还没有记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stats.commonBand?.let { "常见 $it = 相邻两小时之和最大，指标卡沿用同一个值" }
                ?: "暂无时段数据",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HourlyChart(stats: PeriodStats, colors: StatsColors) {
    val progress = remember(stats.window) { Animatable(0f) }
    LaunchedEffect(stats.window) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.label)
    val bandStart = stats.commonBandStartHour

    Canvas(modifier = Modifier.fillMaxWidth().height(128.dp)) {
        // 轴标签会跟随系统字体放大，行高必须按实际字号算，否则放大后会被裁掉
        val gutter = (10.sp.toPx() * 2.2f).coerceAtLeast(22.dp.toPx())
        val top = 8.dp.toPx()
        val bottom = size.height - gutter
        val chartHeight = bottom - top
        val slot = size.width / 24f
        val barWidth = (slot * 0.55f).coerceIn(2.dp.toPx(), 12.dp.toPx())
        val maxValue = (stats.hourlyCounts.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()

        repeat(3) { line ->
            val y = top + chartHeight * line / 2f
            drawLine(
                color = colors.grid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        stats.hourlyCounts.forEachIndexed { hour, count ->
            if (count <= 0) return@forEachIndexed
            val centerX = slot * hour + slot / 2f
            val revealed = ((progress.value * 28f - hour) / 4f).coerceIn(0f, 1f)
            val barHeight = chartHeight * (count / maxValue) * revealed
            if (barHeight <= 0.5f) return@forEachIndexed
            val highlighted = bandStart != null && (hour == bandStart || hour == bandStart + 1)
            drawRoundRect(
                color = if (highlighted) colors.accent else colors.barIdle,
                topLeft = Offset(centerX - barWidth / 2f, bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(minOf(3.dp.toPx(), barHeight / 2f))
            )
        }

        listOf(0, 6, 12, 18, 23).forEach { hour ->
            val centerX = slot * hour + slot / 2f
            val layout = textMeasurer.measure(
                text = AnnotatedString(String.format(Locale.CHINA, "%02d", hour)),
                style = labelStyle
            )
            val x = (centerX - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(textLayoutResult = layout, topLeft = Offset(x, bottom + 5.dp.toPx()))
        }
    }
}

/**
 * 形态分布：Bristol 1–7 型柱状图 + 排便量 / 费力程度堆叠条 + 三项身体感受。
 * 详情字段全部可空，每个分组各自标注「已填写 N / 共 M 条」，未填写不算作任何分箱。
 */
@Composable
internal fun DetailDistributionCard(stats: PeriodStats, colors: StatsColors, modifier: Modifier = Modifier) {
    val distribution = stats.distribution
    val stacked = LocalDensity.current.fontScale > 1.3f
    // 图表对读屏没有意义，收敛成一句汇总：整体分母 + 是否有详情 + 两组主要分箱
    val summary = buildString {
        append("形态分布，已填写 ${distribution.filledBristol} / 共 ${stats.total} 条")
        when {
            !stats.hasData -> append("，这个周期还没有记录")
            !distribution.anyFilled -> append("，本周期的记录都还没填详情")
            else -> {
                append("；布里斯托 1–7 型 ")
                append(distribution.bristolCounts.joinToString("、"))
                append("；排便量 ")
                append(distribution.amountCounts.joinToString("、"))
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CardShape)
            .padding(16.dp)
            .clearAndSetSemantics { contentDescription = summary }
    ) {
        if (stacked) {
            Text(
                text = "形态分布",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "已填写 ${distribution.filledBristol} / 共 ${stats.total} 条",
                style = MaterialTheme.typography.bodySmall,
                color = colors.label.copy(alpha = 0.7f)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "形态分布",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "已填写 ${distribution.filledBristol} / 共 ${stats.total} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.label.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "未填写不计入；快速打卡的记录没有详情",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        when {
            !stats.hasData -> EmptyHint("这个周期还没有记录")
            !distribution.anyFilled -> EmptyHint("本周期的记录都还没填详情")
            else -> {
                BristolChart(distribution = distribution, colors = colors)
                Spacer(Modifier.height(14.dp))
                DistributionRow(
                    label = "排便量",
                    labels = listOf("少量", "适中", "大量"),
                    counts = distribution.amountCounts,
                    filled = distribution.filledAmount,
                    total = stats.total,
                    colors = colors
                )
                Spacer(Modifier.height(12.dp))
                DistributionRow(
                    label = "费力程度",
                    labels = listOf("不费力", "轻微", "明显"),
                    counts = distribution.effortCounts,
                    filled = distribution.filledEffort,
                    total = stats.total,
                    colors = colors
                )
                Spacer(Modifier.height(12.dp))
                SensationRows(distribution = distribution)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Bristol 合计 ${distribution.bristolCounts.sum()} = 已填写 " +
                        "${distribution.filledBristol} ≤ 共 ${stats.total} 条",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BristolChart(distribution: DetailDistribution, colors: StatsColors) {
    val maxValue = (distribution.bristolCounts.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
    // 大字号下标签会变高，固定 86dp 会把数字标签裁掉
    val fontScale = LocalDensity.current.fontScale
    val chartHeight = if (fontScale > 1.3f) 112.dp else 86.dp
    val maxBarHeight = if (fontScale > 1.3f) 64f else 56f
    Text(
        text = "布里斯托 1–7 型",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
        distribution.bristolCounts.forEachIndexed { index, count ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(((count / maxValue) * maxBarHeight).coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.accent.copy(alpha = if (count > 0) 0.4f else 0.08f))
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = (index + 1).toString(),
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = colors.label
                )
            }
        }
    }
}

@Composable
private fun DistributionRow(
    label: String,
    labels: List<String>,
    counts: List<Int>,
    filled: Int,
    total: Int,
    colors: StatsColors
) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "已填写 $filled / 共 $total 条",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "已填写 $filled / 共 $total 条",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    StackedBar(values = counts, colors = colors)
    Spacer(Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, text ->
            Text(
                text = "$text ${counts[index]}",
                fontSize = 11.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StackedBar(values: List<Int>, colors: StatsColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.grid)
    ) {
        values.filter { it > 0 }.forEachIndexed { index, value ->
            Box(
                modifier = Modifier
                    .weight(value.toFloat())
                    .fillMaxHeight()
                    .background(colors.accent.copy(alpha = (0.45f - index * 0.13f).coerceAtLeast(0.14f)))
            )
        }
    }
}

@Composable
private fun SensationRows(distribution: DetailDistribution) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Text(
        text = "身体感受",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(4.dp))
    listOf(
        "便意急迫" to (distribution.urgencyCount to distribution.filledUrgency),
        "腹部不适" to (distribution.abdominalCount to distribution.filledAbdominal),
        "排不净感" to (distribution.incompleteCount to distribution.filledIncomplete)
    ).forEach { (label, counts) ->
        val valueText = "${counts.first} 条 / 已填写 ${counts.second} 条"
        if (stacked) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = valueText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = valueText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
