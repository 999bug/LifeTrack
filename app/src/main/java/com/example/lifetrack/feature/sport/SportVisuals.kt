package com.example.lifetrack.feature.sport

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.ui.LifeTrackTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

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
private val pillShape = RoundedCornerShape(50)
private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

/**
 * 同色微渐变（v2 视觉升级）：模块色低透明度从顶部淡出到卡面，只做质感不做装饰色。
 * [soft] 是概览卡的更淡版本；深浅两套透明度与已确认原型一致。
 */
@Composable
private fun sportWashBrush(accent: Color, surface: Color, soft: Boolean): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = accent.copy(alpha = if (soft) (if (dark) 0.09f else 0.075f) else (if (dark) 0.15f else 0.13f))
    val mid = accent.copy(alpha = if (dark) 0.05f else 0.045f)
    return if (soft) {
        Brush.verticalGradient(0f to top, 0.62f to surface, 1f to surface)
    } else {
        Brush.verticalGradient(0f to top, 0.44f to mid, 1f to surface)
    }
}

/** 小胶囊：时长 / 距上次 / 动作提示共用；文字用 primary，深浅两套都过了 AA。 */
@Composable
private fun SportPill(
    text: String,
    idle: Boolean = false,
    dot: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(
                if (idle) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        val tint = if (idle) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        if (dot) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(tint))
        }
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (idle) FontWeight.Normal else FontWeight.Medium,
            color = tint
        )
    }
}

/** 类型图标底座：pill 底 + 模块色图标 + 一圈同色细描边，快记卡 / 记录行共用。 */
@Composable
private fun SportIconBubble(sport: String, accent: Color, size: Int = 40, iconSize: Int = 22) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape((size * 0.32f).dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = sportIcon(sport),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/**
 * 类型纹理：瑜伽呼吸环 / 骑行轮组 / 跑步路线，低透明度画在卡片右上角。
 * 纯装饰（无读屏语义），只借用模块色，不引入新色相。
 */
@Composable
private fun SportMotif(sport: String, accent: Color, modifier: Modifier = Modifier) {
    val color = accent.copy(alpha = 0.17f)
    Canvas(modifier = modifier) {
        val u = size.minDimension / 100f
        val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
        when (sport) {
            "yoga" -> {
                val c = Offset(66f * u, 30f * u)
                listOf(10f, 19f, 28f).forEach { r -> drawCircle(color, r * u, c, style = stroke) }
                drawCircle(color, 2.6f * u, c)
            }
            "ride" -> {
                val c = Offset(70f * u, 28f * u)
                drawCircle(color, 26f * u, c, style = stroke)
                drawCircle(color, 4f * u, c, style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    drawLine(
                        color = color,
                        start = c,
                        end = Offset(c.x + cos(a).toFloat() * 26f * u, c.y + sin(a).toFloat() * 26f * u),
                        strokeWidth = 1.6f * u,
                        cap = StrokeCap.Round
                    )
                }
            }
            else -> {
                val path = Path().apply {
                    moveTo(8f * u, 84f * u)
                    cubicTo(28f * u, 76f * u, 38f * u, 48f * u, 60f * u, 40f * u)
                    cubicTo(74f * u, 35f * u, 88f * u, 22f * u, 94f * u, 6f * u)
                }
                drawPath(path, color, style = stroke)
                drawCircle(color, 3f * u, Offset(60f * u, 40f * u), style = stroke)
                listOf(Triple(6f, 62f, 20f), Triple(2f, 72f, 14f), Triple(12f, 52f, 21f)).forEach { (x, y, x2) ->
                    drawLine(
                        color = color,
                        start = Offset(x * u, y * u),
                        end = Offset(x2 * u, y * u),
                        strokeWidth = 1.6f * u,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/** 虚线描边：空态提示框与未来节奏格共用，「还没到 / 还没有」不靠颜色单独表意。 */
private fun Modifier.dashedSportBorder(color: Color, cornerDp: Float): Modifier = drawBehind {
    val strokePx = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(strokePx / 2, strokePx / 2),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(cornerDp.dp.toPx()),
        style = Stroke(width = strokePx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
    )
}

/**
 * 本周概览：次数 / 合计时长 / 距上次胶囊 / 三类占比条 / 七天节奏格（可点看当天）。
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
    stacked: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(sportWashBrush(moduleColor, surface, soft = true))
            .border(1.dp, moduleColor.copy(alpha = 0.24f), cardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本周概览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            SportPill(text = gap, idle = week.isIdle, dot = true)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${week.count}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = " 次",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                if (!stacked && !week.isIdle) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "共 ${SportFields.minutesLabel(week.totalMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            if (stacked || week.isIdle) {
                Text(
                    text = if (week.isIdle) "这周还没有记录" else "共 ${SportFields.minutesLabel(week.totalMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        WeekRhythm(week = week, moduleColor = moduleColor)
    }
}

@Composable
private fun WeekBar(week: SportWeek, moduleColor: Color, surface: Color) {
    val segments = week.knownMinutes
    val legacy = week.legacyMinutes
    val total = segments.sumOf { it.second } + legacy
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
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (total > 0) {
            val count = segments.size + if (legacy > 0) 1 else 0
            segments.forEachIndexed { index, (_, minutes) ->
                Box(
                    modifier = Modifier
                        .weight(minutes.toFloat())
                        .widthIn(min = 4.dp)
                        .fillMaxHeight()
                        .clip(segmentShape(index == 0, index == count - 1))
                        .background(sportShade(moduleColor, surface, index))
                )
            }
            if (legacy > 0) {
                Box(
                    modifier = Modifier
                        .weight(legacy.toFloat())
                        .widthIn(min = 4.dp)
                        .fillMaxHeight()
                        .clip(segmentShape(segments.isEmpty(), true))
                        .background(moduleColor.copy(alpha = 0.28f))
                )
            }
        }
    }
}

private fun segmentShape(first: Boolean, last: Boolean): RoundedCornerShape = RoundedCornerShape(
    topStart = if (first) 6.dp else 3.dp,
    bottomStart = if (first) 6.dp else 3.dp,
    topEnd = if (last) 6.dp else 3.dp,
    bottomEnd = if (last) 6.dp else 3.dp
)

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 七天节奏：一格一天，高度按当天分钟数归一到本周峰值；点格子在下方看当天分钟数与次数。
 * 未来日期用虚线底槽表示「还没到」，今天实心加一圈描边，不靠颜色单独表意。
 */
@Composable
private fun WeekRhythm(week: SportWeek, moduleColor: Color) {
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = week.days.firstOrNull { it.date.toString() == selectedDate }
        ?: week.days.firstOrNull { it.isToday }
        ?: week.days.first()
    val maxMinutes = week.days.maxOfOrNull { it.minutes } ?: 0
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val futureColor = MaterialTheme.colorScheme.outlineVariant
    val summary = week.days.joinToString("，") { day ->
        "${day.label}${if (day.minutes > 0) " ${day.minutes} 分钟" else " 无记录"}"
    }
    val info = "周${selected.label} · " +
        if (selected.count > 0) "${selected.minutes} 分钟 · ${selected.count} 次" else "无记录"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = "本周节奏",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "点格子看当天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                val isSelected = day.date == selected.date
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedDate = day.date.toString() }
                        .semantics {
                            role = Role.Button
                            this.selected = isSelected
                            contentDescription = "周${day.label}，" +
                                if (day.count > 0) "${day.minutes} 分钟，${day.count} 次" else "无记录"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    day.isFuture -> Color.Transparent
                                    day.minutes > 0 -> moduleColor.copy(alpha = if (day.isToday) 1f else 0.55f)
                                    else -> trackColor
                                }
                            )
                            .then(
                                when {
                                    day.isToday -> Modifier.border(1.5.dp, moduleColor, RoundedCornerShape(6.dp))
                                    day.isFuture -> Modifier.dashedSportBorder(futureColor, 6f)
                                    else -> Modifier
                                }
                            )
                    )
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (day.isToday || isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        Text(
            text = info,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/**
 * 快记卡（v2）：同色微渐变 + 类型纹理 + 本周数据 + 上次时长胶囊，点一下直接开弹层并预选类型。
 * 副行只报事实，不给目标值、不给建议，避免变成打卡任务。
 */
@Composable
internal fun SportQuickCard(
    sport: String,
    last: SportSession?,
    weekCount: Int,
    weekMinutes: Int,
    accent: Color,
    stacked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = SportFields.sportLabel(sport)
    val weekLine = if (weekCount > 0) "本周 $weekCount 次 · $weekMinutes 分" else "本周还没记录"
    val lastLine = last?.let { "上次 ${SportFields.minutesLabel(it.minutes)}" } ?: "还没记录"
    val description = "$name，记一次。$weekLine，$lastLine"
    val wash = sportWashBrush(accent, MaterialTheme.colorScheme.surface, soft = false)
    val borderColor = accent.copy(alpha = 0.24f)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animationsEnabled = Settings.Global.getFloat(
        LocalContext.current.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) != 0f
    val scale by animateFloatAsState(
        targetValue = if (pressed && animationsEnabled) 0.97f else 1f,
        animationSpec = tween(120),
        label = "sportQuickPress"
    )
    val cardModifier = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(cardShape)
        .background(wash)
        .border(1.dp, borderColor, cardShape)
        .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
        .semantics {
            role = Role.Button
            contentDescription = description
        }

    if (stacked) {
        Row(
            modifier = cardModifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SportIconBubble(sport = sport, accent = accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = weekLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SportPill(text = lastLine, idle = last == null)
            }
            SportPill(text = "记一次", leadingIcon = Icons.Filled.Add)
        }
    } else {
        Box(modifier = cardModifier.heightIn(min = 150.dp)) {
            SportMotif(
                sport = sport,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(88.dp)
                    .graphicsLayer { translationX = 12.dp.toPx(); translationY = -10.dp.toPx() }
            )
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    SportIconBubble(sport = sport, accent = accent)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = weekLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(6.dp))
                SportPill(text = lastLine, idle = last == null)
            }
        }
    }
}

/**
 * 一条历史记录：类型图标 + 名称 + 时长胶囊，副行给日期时间、强度与备注。
 * 整行点开只读详情；编辑 / 删除保留为独立按钮，200% 字体时挪到下一行右对齐，不挤压正文。
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val accent = LifeTrackTokens.ModuleColors.of(SportFields.TYPE_CODE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), cardShape)
            .clickable(onClick = onShowDetail)
            .heightIn(min = 56.dp)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SportIconBubble(sport = session.sport, accent = accent, size = 36, iconSize = 19)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SportFields.sportLabel(session.sport),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SportPill(text = SportFields.minutesLabel(session.minutes))
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

/** 空态提示：虚线框 + 一句引导，与未来节奏格同一套「还没有」语言。 */
@Composable
internal fun SportEmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .dashedSportBorder(MaterialTheme.colorScheme.outlineVariant, 16f)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 简报折叠壳：与经期「周期洞察」同一套交互（整行可点、读屏报展开态）。 */
@Composable
internal fun SportInsightSection(
    expanded: Boolean,
    accent: Color,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = "本期简报",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
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

/** 简报内的四项数字：常规字体两列，200% 字体单列堆叠；每项一块浅底小砖。 */
@Composable
internal fun SportBriefGrid(brief: SportBrief, stacked: Boolean) {
    val metrics = listOf(
        (if (brief.weekCount > 0) SportFields.minutesLabel(brief.weekMinutes) else "—") to "本周合计时长",
        (if (brief.weekCount > 0) "${brief.avgMinutes} 分钟" else "—") to "本周平均每次",
        "${brief.monthCount} 次" to "近 4 周次数",
        (brief.topSport?.let { "${SportFields.sportLabel(it)} ${brief.topSportCount} 次" } ?: "—") to "近 4 周最常见"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stacked) {
            metrics.forEach { (value, label) -> BriefMetric(value, label, Modifier.fillMaxWidth()) }
        } else {
            metrics.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (value, label) -> BriefMetric(value, label, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BriefMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
