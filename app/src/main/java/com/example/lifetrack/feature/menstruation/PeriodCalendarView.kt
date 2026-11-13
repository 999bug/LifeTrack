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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * 经期置顶日历（界面定稿 v2 · 批 2）。
 *
 * 与旧版的三处差别：
 * 1. **连续经期色带**：相邻经期日画成一条不断开的圆角带（在周 Row 的 drawBehind 上），
 *    格子本身透明；今天不再是实心色块切断色带，改成 2dp 主色环 + 粗体数字。
 * 2. 「回到今天」补齐翻月后的回程；三个导航按钮全部 ≥44dp。
 * 3. 尾部无日期的空周不再渲染（模型仍返回 42 格，模型与既有测试零改动），
 *    日历变矮后首屏能同时露出主行动按钮。
 *
 * 只切换下方选中日、不打开弹层；未来日期的只读状态由 [PeriodDayForm] 决定。
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
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberStatsColors()
    val moduleColor = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val fontScale = LocalDensity.current.fontScale
    val minCellHeight = if (fontScale > 1.3f) 56.dp else 40.dp
    // 模型固定 6 行，视图只裁掉尾部整周空白：11 月这类 5 行月份首屏能省下 40dp。
    val weeks = month.cells.chunked(7).dropLastWhile { week -> week.all { it == null } }

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
            TextButton(
                onClick = onToday,
                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = "回到今天",
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "上个月",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(44.dp)) {
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
        weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val cellWidth = this.size.width / 7f
                        val pad = 2.dp.toPx()
                        var i = 0
                        while (i < 7) {
                            val date = week[i]
                            if (date != null && date in recordedDates) {
                                var j = i
                                while (j + 1 < 7) {
                                    val next = week[j + 1]
                                    if (next == null || next !in recordedDates) break
                                    j++
                                }
                                drawRoundRect(
                                    color = moduleColor.copy(alpha = 0.16f),
                                    topLeft = Offset(i * cellWidth + pad, pad),
                                    size = Size((j - i + 1) * cellWidth - pad * 2, this.size.height - pad * 2),
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                )
                                i = j + 1
                            } else {
                                i++
                            }
                        }
                    }
            ) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendSwatch(label = "经期连续色带") {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(moduleColor.copy(alpha = 0.35f))
                )
            }
            LegendSwatch(label = "预计日虚线圈") {
                Box(
                    Modifier
                        .size(12.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = moduleColor.copy(alpha = 0.85f),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                                )
                            )
                        }
                )
            }
            LegendSwatch(label = "今天 2dp 环") {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val recordedInMonth = month.cells.count { it != null && it in recordedDates }
        val predictedInMonth = month.cells.count { it != null && it in predictedDates && it !in recordedDates }
        Text(
            text = "本月已记录 $recordedInMonth 天 · 预计 $predictedInMonth 天",
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

    // 经期底色由周 Row 画成连续带，这里只保留选中日的淡底，不切断色带。
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }
    val contentColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        isRecorded -> moduleColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dashed = isPredicted && !isRecorded && !isToday
    val ring = isToday || isSelected

    Box(
        modifier = Modifier
            .weight(1f)
            .padding(2.dp)
            .heightIn(min = cellHeight)
            .clip(shape)
            .background(container)
            .then(
                // 经期不画逐格边框：连续带由周 Row 一次画完，逐格描边会把它切回三段方框。
                if (ring) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
            )
            .drawBehind {
                if (dashed) {
                    drawRoundRect(
                        color = moduleColor.copy(alpha = 0.85f),
                        topLeft = Offset.Zero,
                        size = this.size,
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
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
                    fontWeight = if (isToday) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    maxLines = 1,
                    color = contentColor
                )
                // 固定高度的圆点行：已记录才点亮，保证同月格子等高。
                Box(modifier = Modifier.height(6.dp), contentAlignment = Alignment.Center) {
                    if (isRecorded) {
                        PeriodSymptomDot(
                            color = moduleColor,
                            size = 5.dp
                        )
                    }
                }
            }
        }
    }
}
