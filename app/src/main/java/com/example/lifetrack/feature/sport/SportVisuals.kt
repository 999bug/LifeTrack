package com.example.lifetrack.feature.sport

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.ui.LifeTrackTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 运动模块的图标：三类各自一个，旧类型统一退回跑步图标（只在图例里出现）。 */
internal fun sportIcon(sport: String): ImageVector = when (sport) {
    "yoga" -> Icons.Filled.SelfImprovement
    "ride" -> Icons.AutoMirrored.Filled.DirectionsBike
    else -> Icons.AutoMirrored.Filled.DirectionsRun
}

/**
 * 同色相三档明度：浅色主题下模块色是深绿，往白里调；深色主题下是浅绿，往黑里调。
 * 不引入新色相，颜色只做识别，文字图例始终同步出现。
 */
internal fun sportShade(moduleColor: Color, background: Color, level: Int): Color = when (level) {
    0 -> moduleColor
    1 -> lerp(moduleColor, background, 0.34f)
    else -> lerp(moduleColor, background, 0.58f)
}

private val cardShape = LifeTrackTokens.Shapes.card
private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

/**
 * 本周概览：次数 / 合计时长 / 距上次 / 三类占比条 / 七天节奏格。
 *
 * 占比条按分钟数归一，旧类型（步行 / 游泳 / 力量 / 其他）合并成一段中性色，
 * 不冒充三类之一；没有数据时整条退成底槽，不留一条假的分段。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SportWeekCard(
    week: SportWeek,
    gap: String,
    moduleColor: Color,
    surface: Color,
    border: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(surface)
            .border(1.dp, border, cardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "本周",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "${week.count}", style = MaterialTheme.typography.displaySmall)
                    Text(
                        text = " 次",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Text(
                    text = if (week.isIdle) {
                        "这周还没有记录"
                    } else {
                        "合计 ${SportFields.minutesLabel(week.totalMinutes)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = gap,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        WeekBar(week = week, moduleColor = moduleColor, surface = surface)

        if (week.knownMinutes.isNotEmpty() || week.legacyMinutes > 0) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.knownMinutes.forEachIndexed { index, (sport, minutes) ->
                    LegendItem(
                        label = "${SportFields.sportLabel(sport)} ${minutes} 分",
                        color = sportShade(moduleColor, surface, index)
                    )
                }
                if (week.legacyMinutes > 0) {
                    LegendItem(
                        label = "其他旧类型 ${week.legacyMinutes} 分",
                        color = moduleColor.copy(alpha = 0.28f)
                    )
                }
            }
        }

        WeekRhythm(week = week, moduleColor = moduleColor)
    }
}

@Composable
private fun WeekBar(week: SportWeek, moduleColor: Color, surface: Color) {
    val segments = week.knownMinutes
    val legacy = week.legacyMinutes
    val total = segments.sumOf { it.second } + legacy
    val barShape = RoundedCornerShape(5.dp)
    val summary = if (total == 0) {
        "本周还没有运动记录"
    } else {
        buildString {
            append("本周占比：")
            append(
                segments.joinToString("，") {
                    "${SportFields.sportLabel(it.first)} ${it.second} 分钟"
                }
            )
            if (legacy > 0) append("，其他旧类型 $legacy 分钟")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(barShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = summary }
    ) {
        if (total > 0) {
            segments.forEachIndexed { index, (_, minutes) ->
                Box(
                    modifier = Modifier
                        .weight(minutes.toFloat())
                        .fillMaxHeight()
                        .background(sportShade(moduleColor, surface, index))
                )
            }
            if (legacy > 0) {
                Box(
                    modifier = Modifier
                        .weight(legacy.toFloat())
                        .fillMaxHeight()
                        .background(moduleColor.copy(alpha = 0.28f))
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 七天节奏：一格一天，高度按当天分钟数归一到本周峰值。
 * 未来日期用更淡的底槽表示「还没到」，今天加一圈描边，不靠颜色单独表意。
 */
@Composable
private fun WeekRhythm(week: SportWeek, moduleColor: Color) {
    val maxMinutes = week.days.maxOfOrNull { it.minutes } ?: 0
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val summary = week.days.joinToString("，") { day ->
        "${day.label}${if (day.minutes > 0) " ${day.minutes} 分钟" else " 无记录"}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "本周节奏：$summary" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        week.days.forEach { day ->
            val height = when {
                day.minutes <= 0 -> 8.dp
                maxMinutes <= 0 -> 8.dp
                else -> (8f + day.minutes.toFloat() / maxMinutes * 26f).dp
            }
            val color = when {
                day.minutes > 0 -> moduleColor
                day.isFuture -> trackColor.copy(alpha = 0.45f)
                else -> trackColor
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                        .then(
                            if (day.isToday) {
                                Modifier.border(1.dp, moduleColor, RoundedCornerShape(6.dp))
                            } else {
                                Modifier
                            }
                        )
                )
                Text(
                    text = day.label,
                    fontSize = 10.sp,
                    color = if (day.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 快记卡：点一下直接开已有弹层并预选类型，两步记完。
 * 副行只写「上次 X 分钟」或「还没记录」，不给目标值、不给建议，避免变成打卡任务。
 */
@Composable
internal fun SportQuickCard(
    sport: String,
    last: SportSession?,
    accent: Color,
    stacked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = last?.let { "上次 ${SportFields.minutesLabel(it.minutes)}" } ?: "还没记录"
    val description = "${SportFields.sportLabel(sport)}：记一次"
    val clickModifier = Modifier
        .clickable(onClick = onClick)
        .semantics {
            role = Role.Button
            contentDescription = description
        }

    if (stacked) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), cardShape)
                .then(clickModifier)
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SportIconBubble(sport = sport, accent = accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = SportFields.sportLabel(sport), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = "记一次", style = MaterialTheme.typography.labelLarge, color = accent)
        }
    } else {
        Column(
            modifier = modifier
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), cardShape)
                .then(clickModifier)
                .height(108.dp)
                .padding(12.dp)
        ) {
            SportIconBubble(sport = sport, accent = accent)
            Spacer(Modifier.weight(1f))
            Text(
                text = SportFields.sportLabel(sport),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SportIconBubble(sport: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = sportIcon(sport),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 一条历史记录：主行给类型与时长，副行给日期时间、强度与备注。
 * 整行点开只读详情；编辑 / 删除保留为独立按钮，200% 字体时挪到下一行右对齐，不挤压正文。
 */
@Composable
internal fun SportHistoryRow(
    session: SportSession,
    today: LocalDate,
    stacked: Boolean,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = when (session.date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> session.date.format(dateFormatter)
    }
    val meta = buildList {
        add(dateLabel)
        session.intensity.takeIf { it.isNotEmpty() }?.let { add(SportFields.intensityLabel(it)) }
        if (session.note.isNotBlank()) add(session.note)
    }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), cardShape)
            .clickable(onClick = onShowDetail)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(text = SportFields.sportLabel(session.sport), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = SportFields.minutesLabel(session.minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (stacked) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!stacked) {
                RowActionIcons(onEdit = onEdit, onDelete = onDelete)
            }
        }
        if (stacked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                RowActionIcons(onEdit = onEdit, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun RowActionIcons(onEdit: () -> Unit, onDelete: () -> Unit) {
    Row {
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "编辑这条运动记录")
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除这条运动记录")
        }
    }
}

/** 简报折叠壳：与经期「周期洞察」同一套交互（整行可点、读屏报展开态）。 */
@Composable
internal fun SportInsightSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), cardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(cardShape)
                .clickable { onToggle() }
                .semantics {
                    stateDescription = if (expanded) "已展开" else "已收起"
                    contentDescription = "本期简报"
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本期简报",
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

/** 简报内的四项数字：常规字体两列，200% 字体单列堆叠。 */
@Composable
internal fun SportBriefGrid(brief: SportBrief, stacked: Boolean) {
    val metrics = listOf(
        (if (brief.weekCount > 0) SportFields.minutesLabel(brief.weekMinutes) else "—") to "本周合计时长",
        (if (brief.weekCount > 0) "${brief.avgMinutes} 分钟" else "—") to "本周平均每次",
        "${brief.monthCount} 次" to "近 4 周次数",
        (brief.topSport?.let { "${SportFields.sportLabel(it)} ${brief.topSportCount} 次" } ?: "—") to "近 4 周最常见"
    )
    if (stacked) {
        metrics.forEach { (value, label) -> BriefMetric(value, label, Modifier.fillMaxWidth()) }
    } else {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (value, label) -> BriefMetric(value, label, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BriefMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
