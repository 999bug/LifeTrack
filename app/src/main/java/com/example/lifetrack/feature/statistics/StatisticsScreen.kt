package com.example.lifetrack.feature.statistics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.feature.home.MainViewModel
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(18.dp)
private val MetricShape = RoundedCornerShape(14.dp)

@Composable
fun StatisticsScreen(viewModel: MainViewModel) {
    val statsByPeriod by viewModel.statistics.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { StatsPeriod.entries.size })
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    val period = StatsPeriod.entries[pagerState.currentPage]
    val current = statsByPeriod[period]
    val colors = rememberStatsColors()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            HeaderRow(
                label = current?.window?.label.orEmpty(),
                canGoNext = current?.window?.lastDate?.isBefore(today) == true,
                onPrevious = { viewModel.shiftStatistics(period, -1) },
                onNext = { viewModel.shiftStatistics(period, 1) },
                colors = colors
            )
            Spacer(Modifier.height(14.dp))
            PeriodSwitcher(
                selected = period,
                onSelect = { target -> scope.launch { pagerState.animateScrollToPage(target.ordinal) } },
                colors = colors
            )
            Spacer(Modifier.height(4.dp))
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            val pagePeriod = StatsPeriod.entries[page]
            val stats = statsByPeriod[pagePeriod]
            if (stats != null) {
                StatsPage(stats = stats, today = today, colors = colors)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun HeaderRow(
    label: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    colors: StatsColors
) {
    // 字体放大后标题与周期胶囊并排会互相挤压，改为上下两行。
    val stacked = LocalDensity.current.fontScale > 1.3f
    if (stacked) {
        Column {
            ScreenTitle()
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PeriodPill(label, canGoNext, onPrevious, onNext, colors)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(Modifier.weight(1f))
            PeriodPill(label, canGoNext, onPrevious, onNext, colors)
        }
    }
}

@Composable
private fun ScreenTitle(modifier: Modifier = Modifier) {
    Text(
        text = "统计",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

@Composable
private fun PeriodPill(
    label: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    colors: StatsColors
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "上一周期",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 76.dp).padding(horizontal = 6.dp)
        )
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "下一周期",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canGoNext) 1f else 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PeriodSwitcher(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    colors: StatsColors
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(19.dp))
    ) {
        val segmentWidth = maxWidth / StatsPeriod.entries.size
        val indicator by animateDpAsState(
            targetValue = segmentWidth * selected.ordinal,
            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow),
            label = "periodIndicator"
        )
        Box(
            modifier = Modifier
                .offset(x = indicator)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.accent)
        )
        Row(modifier = Modifier.fillMaxSize()) {
            StatsPeriod.entries.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.label(),
                        fontSize = 13.sp,
                        fontWeight = if (item == selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (item == selected) {
                            colors.onAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsPage(stats: PeriodStats, today: LocalDate, colors: StatsColors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        HeroBlock(stats = stats, colors = colors)
        Spacer(Modifier.height(18.dp))
        ChartCard(stats = stats, today = today, colors = colors)
        Spacer(Modifier.height(12.dp))
        MetricGrid(stats = stats, colors = colors)
        Spacer(Modifier.height(12.dp))
        InsightCard(stats = stats, colors = colors)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "统计仅基于已保存的记录，不构成医疗诊断。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HeroBlock(stats: PeriodStats, colors: StatsColors) {
    val animatedTotal by animateIntAsState(
        targetValue = stats.total,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "totalCount"
    )
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val heroSize = (60f / fontScale).coerceAtLeast(40f).sp

    Column {
        Text(
            text = "共记录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = animatedTotal.toString(),
                fontSize = heroSize,
                lineHeight = heroSize * 1.04f,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "次",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (stats.hasData && stats.previousTotal > 0) {
                Spacer(Modifier.width(12.dp))
                DeltaLabel(
                    delta = stats.delta,
                    colors = colors,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DeltaLabel(delta: Int, colors: StatsColors, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, tint) = when {
        delta > 0 -> "↑ $delta 较上期" to colors.accent
        delta < 0 -> "↓ ${-delta} 较上期" to muted
        else -> "与上期持平" to muted
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        modifier = modifier
    )
}

@Composable
private fun ChartCard(stats: PeriodStats, today: LocalDate, colors: StatsColors) {
    val caption = if (stats.window.period == StatsPeriod.YEAR) "每月次数" else "每日次数"
    val hint = when {
        !stats.hasData -> ""
        stats.window.contains(today) && stats.window.period == StatsPeriod.YEAR -> "高亮为本月"
        stats.window.contains(today) -> "高亮为今天"
        else -> "高亮为峰值"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CardShape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.label.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "共 ${stats.total} 次，单日最高 ${stats.peakValue} 次"
                }
        ) {
            StatsChart(stats = stats, colors = colors)
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
    }
}

@Composable
private fun StatsChart(stats: PeriodStats, colors: StatsColors) {
    val progress = remember(stats.window) { Animatable(0f) }
    LaunchedEffect(stats.window) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.label)
    val activeLabelStyle = TextStyle(fontSize = 10.sp, color = colors.accent, fontWeight = FontWeight.Medium)

    Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        if (stats.bars.isEmpty()) return@Canvas

        // 轴标签会跟随系统字体放大，行高必须按实际字号算，否则放大后会被裁掉
        val gutter = (10.sp.toPx() * 2.2f).coerceAtLeast(22.dp.toPx())
        val top = 8.dp.toPx()
        val bottom = size.height - gutter
        val chartHeight = bottom - top
        val slot = size.width / stats.bars.size
        val barWidth = (slot * 0.5f).coerceIn(3.dp.toPx(), 16.dp.toPx())
        val maxValue = stats.maxBarValue.toFloat()

        repeat(3) { line ->
            val y = top + chartHeight * line / 2f
            drawLine(
                color = colors.grid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        stats.bars.forEachIndexed { index, bar ->
            val centerX = slot * index + slot / 2f
            val revealed = ((progress.value * (stats.bars.size + 4) - index) / 4f).coerceIn(0f, 1f)
            val barHeight = chartHeight * (bar.value / maxValue) * revealed

            when {
                barHeight > 0.5f -> drawRoundRect(
                    color = if (bar.highlighted) colors.accent else colors.barIdle,
                    topLeft = Offset(centerX - barWidth / 2f, bottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(minOf(3.dp.toPx(), barHeight / 2f))
                )

                stats.hasData && bar.value == 0 -> drawRoundRect(
                    color = colors.grid,
                    topLeft = Offset(centerX - barWidth / 2f, bottom - 2.dp.toPx()),
                    size = Size(barWidth, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }

        if (!stats.hasData) return@Canvas

        stats.bars.forEachIndexed { index, bar ->
            if (!bar.showLabel) return@forEachIndexed
            val centerX = slot * index + slot / 2f
            val layout = textMeasurer.measure(
                text = AnnotatedString(bar.label),
                style = if (bar.highlighted) activeLabelStyle else labelStyle
            )
            val x = (centerX - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(textLayoutResult = layout, topLeft = Offset(x, bottom + 5.dp.toPx()))
        }
    }
}

@Composable
private fun MetricGrid(stats: PeriodStats, colors: StatsColors) {
    val isYear = stats.window.period == StatsPeriod.YEAR
    val averageLabel = if (isYear) "月均" else "日均"
    val averageValue = if (isYear) stats.total / 12.0 else stats.dailyAverage

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = averageLabel,
                value = String.format(Locale.CHINA, "%.1f 次", averageValue),
                colors = colors,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "单日最高",
                value = "${stats.peakValue} 次",
                colors = colors,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = "最长连续",
                value = "${stats.longestStreak} 天",
                colors = colors,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "常见时段",
                value = when {
                    stats.commonBandStartHour == null -> "—"
                    LocalDensity.current.fontScale > 1.3f -> stats.commonBandShort.orEmpty()
                    else -> stats.commonBand.orEmpty()
                },
                colors = colors,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, colors: StatsColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MetricShape)
            .background(colors.surface)
            .border(1.dp, colors.border, MetricShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun InsightCard(stats: PeriodStats, colors: StatsColors) {
    val headline = when {
        !stats.hasData -> "这个周期还没有记录"
        stats.previousTotal == 0 -> "本期首次留下记录，共 ${stats.total} 次"
        stats.delta > 0 -> "比上期多 ${stats.delta} 次"
        stats.delta < 0 -> "比上期少 ${-stats.delta} 次"
        else -> "与上期持平，各 ${stats.total} 次"
    }
    val detail = stats.commonBand?.let { "最常出现在 $it" } ?: "样本还少，暂时看不出常见时段"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(MetricShape)
            .background(colors.surface)
            .border(1.dp, colors.border, MetricShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(colors.accent)
        )
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class StatsColors(
    val accent: Color,
    val onAccent: Color,
    val barIdle: Color,
    val grid: Color,
    val label: Color,
    val surface: Color,
    val border: Color
)

@Composable
private fun rememberStatsColors(): StatsColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return remember(scheme, isDark) {
        if (isDark) {
            StatsColors(
                accent = scheme.primary,
                onAccent = Color(0xFF0E1510),
                barIdle = scheme.primary.copy(alpha = 0.30f),
                grid = Color.White.copy(alpha = 0.07f),
                label = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                surface = scheme.surface,
                border = Color.White.copy(alpha = 0.08f)
            )
        } else {
            StatsColors(
                accent = scheme.primary,
                onAccent = scheme.onPrimary,
                barIdle = scheme.primary.copy(alpha = 0.22f),
                grid = scheme.onSurface.copy(alpha = 0.08f),
                label = scheme.onSurfaceVariant,
                surface = scheme.surface,
                border = scheme.onSurface.copy(alpha = 0.10f)
            )
        }
    }
}

private fun StatsPeriod.label(): String = when (this) {
    StatsPeriod.WEEK -> "周"
    StatsPeriod.MONTH -> "月"
    StatsPeriod.YEAR -> "年"
}
