package com.example.lifetrack.feature.history

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.model.CalendarCell
import com.example.lifetrack.core.model.CalendarMonth
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.statistics.CardShape
import com.example.lifetrack.feature.statistics.rememberStatsColors
import java.time.LocalDate

private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 历史页的月历视图：自然月、周一为首、前后补白不参与计数。 */
@Composable
fun CalendarMonthView(
    month: CalendarMonth,
    today: LocalDate,
    canGoNext: Boolean,
    selectedDate: LocalDate?,
    typeNames: Map<String, String>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberStatsColors()
    val fontScale = LocalDensity.current.fontScale
    // 用最小高度而不是固定高度：大字号下「日期 + 图标行」可以自己把格子撑高，不会被 clip 裁掉；
    // 常规字号也保到 48dp，满足最小触摸目标。
    val minCellHeight = if (fontScale > 1.3f) 72.dp else 48.dp

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
                week.forEach { cell ->
                    CalendarDayCell(
                        cell = cell,
                        today = today,
                        selectedDate = selectedDate,
                        typeNames = typeNames,
                        cellHeight = minCellHeight,
                        fontScale = fontScale,
                        onSelectDate = onSelectDate
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "点按日期查看当天记录",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RowScope.CalendarDayCell(
    cell: CalendarCell,
    today: LocalDate,
    selectedDate: LocalDate?,
    typeNames: Map<String, String>,
    cellHeight: Dp,
    fontScale: Float,
    onSelectDate: (LocalDate) -> Unit
) {
    val date = cell.date
    val shape = RoundedCornerShape(8.dp)
    val isToday = date != null && date == today
    val isSelected = date != null && date == selectedDate
    val marks = DayCalendarData.visibleMarks(cell.types)

    val description = if (date == null) {
        "非本月日期"
    } else {
        val kinds = cell.types.mapNotNull { typeNames[it] }.joinToString("、")
        val base = "${date.monthValue}月${date.dayOfMonth}日，${cell.count} 次"
        if (kinds.isEmpty()) base else "$base，含$kinds"
    }

    // 今天：实心主色 + 反白；被选中（弹层打开）：浅色底 + 主色描边；其余无底色，
    // 「有没有记录」完全靠图标行表达，避免背景深浅和次数两种编码互相干扰。
    val container = when {
        isToday -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val contentColor = if (isToday) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .padding(2.dp)
            .heightIn(min = cellHeight)
            .clip(shape)
            .background(container)
            .border(
                width = if (isSelected && !isToday) 1.dp else 0.dp,
                color = if (isSelected && !isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            )
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
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = if (fontScale > 1.3f) 11.sp else 12.sp,
                    maxLines = 1,
                    color = contentColor
                )
                // 没有记录也保留同样高度的图标行，保证同一个月的格子等高
                Box(modifier = Modifier.height(13.dp), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                        marks.forEach { typeCode ->
                            Icon(
                                imageVector = LifeTrackTokens.ModuleIcons.of(typeCode),
                                contentDescription = null,
                                tint = if (isToday) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    LifeTrackTokens.ModuleColors.of(typeCode)
                                },
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
