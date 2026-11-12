package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PeriodMonth
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.statistics.CardShape
import com.example.lifetrack.feature.statistics.rememberStatsColors
import java.time.LocalDate

private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 经期子应用的置顶日历（设计 §5.4 ②）。与历史页 [com.example.lifetrack.feature.history.CalendarMonthView]
 * 各自独立：这里用可翻未来的 [PeriodMonth]，格子按「实际经期 / 预测经期 / 今天 / 已记录 / 选中」染色，
 * 点日期只切换下方选中日、不打开弹层。格子用 `heightIn` 而非 1:1 正方形，保证 200% 字体下仍留出填写区。
 */
@Composable
fun PeriodCalendarView(
    month: PeriodMonth,
    today: LocalDate,
    recordedDates: Set<LocalDate>,
    predictedDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberStatsColors()
    val moduleColor = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val fontScale = LocalDensity.current.fontScale
    // 比历史月历更矮的最小高度：首屏要同时露出日历与逐行填写区开头（设计 §5.8 第 6 条）。
    val minCellHeight = if (fontScale > 1.3f) 56.dp else 40.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, CardShape)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "上个月",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "下个月",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canGoNext) 1f else 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEK_LABELS.forEach { label ->
                Text(
                    text = label,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        month.cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    PeriodDayCell(
                        date = date,
                        today = today,
                        recordedDates = recordedDates,
                        predictedDates = predictedDates,
                        selectedDate = selectedDate,
                        moduleColor = moduleColor,
                        cellHeight = minCellHeight,
                        fontScale = fontScale,
                        onSelectDate = onSelectDate
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendSwatch(label = "已记录经期") {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(moduleColor.copy(alpha = 0.16f))
                        .border(1.dp, moduleColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                )
            }
            LegendSwatch(label = "预计经期") {
                Box(
                    Modifier
                        .size(12.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = moduleColor.copy(alpha = 0.85f),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                                )
                            )
                        }
                )
            }
            LegendSwatch(label = "今天") {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "可向右翻到未来月份；点任意一天，下方内容跟着切换。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendSwatch(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        swatch()
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.PeriodDayCell(
    date: LocalDate?,
    today: LocalDate,
    recordedDates: Set<LocalDate>,
    predictedDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    moduleColor: Color,
    cellHeight: Dp,
    fontScale: Float,
    onSelectDate: (LocalDate) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val isToday = date != null && date == today
    val isRecorded = date != null && date in recordedDates
    val isPredicted = date != null && date in predictedDates
    val isSelected = date != null && date == selectedDate

    val description = if (date == null) {
        "非本月日期"
    } else {
        buildString {
            append("${date.monthValue}月${date.dayOfMonth}日")
            if (isToday) append("，今天")
            if (isRecorded) append("，已记录经期") else if (isPredicted) append("，预计经期")
            if (isSelected) append("，已选中")
        }
    }

    // 经期中的今天用模块色实心：点「大姨妈来了」当天就能看到变化，
    // 否则「今天」的主色实心会把经期底色完全盖掉。其余今天仍用主色实心。
    val periodToday = isToday && isRecorded
    val container = when {
        periodToday -> moduleColor
        isToday -> MaterialTheme.colorScheme.primary
        isRecorded -> moduleColor.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val contentColor = when {
        periodToday -> onModuleColor(moduleColor)
        isToday -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dashed = isPredicted && !isRecorded && !isToday

    Box(
        modifier = Modifier
            .weight(1f)
            .padding(2.dp)
            .heightIn(min = cellHeight)
            .clip(shape)
            .background(container)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    isRecorded && !isToday -> Modifier.border(1.dp, moduleColor.copy(alpha = 0.7f), shape)
                    else -> Modifier
                }
            )
            .drawBehind {
                if (dashed) {
                    drawRoundRect(
                        color = moduleColor.copy(alpha = 0.85f),
                        topLeft = Offset.Zero,
                        size = this.size,
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
                        )
                    )
                }
            }
            .clickable(
                enabled = date != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { date?.let(onSelectDate) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = if (fontScale > 1.3f) 11.sp else 12.sp,
                    maxLines = 1,
                    color = contentColor
                )
                // 固定高度的圆点行：已记录才点亮，保证同月格子等高。
                Box(modifier = Modifier.height(6.dp), contentAlignment = Alignment.Center) {
                    if (isRecorded) {
                        PeriodSymptomDot(
                            color = if (isToday) contentColor else moduleColor,
                            size = 5.dp
                        )
                    }
                }
            }
        }
    }
}