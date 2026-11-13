package com.example.lifetrack.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 周期视觉语言的唯一来源（界面定稿 v2 · 批 1 / 批 2 共用）。
 *
 * 首页通栏卡用 64dp 迷你环、经期页用 92dp 大环，整周期视图用 28 段连续色带。
 * 这里只画形状，不推算日期、不产出任何健康结论：角度与区间都由调用方从
 * [com.example.lifetrack.core.model.CycleSnapshot] 派生后传入。
 */
data class CycleVisualColors(
    val period: Color,
    val track: Color,
    val primary: Color,
    val text: Color,
    val muted: Color,
    val background: Color
)

@Composable
@ReadOnlyComposable
fun cycleVisualColors(): CycleVisualColors = CycleVisualColors(
    period = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE),
    track = MaterialTheme.colorScheme.surfaceContainerHighest,
    primary = MaterialTheme.colorScheme.primary,
    text = MaterialTheme.colorScheme.onSurface,
    muted = MaterialTheme.colorScheme.onSurfaceVariant,
    background = MaterialTheme.colorScheme.surface
)

/**
 * 周期环：底色轨道 + 经期实色弧 + 预计段虚线弧 + 今天定位点 + 中心天数。
 *
 * [dayIndex] 从 1 起（周期第几天）；[cycleLength] 为推算或配置的周期长度；
 * [periodLength] 为经期长度。角度从 12 点方向顺时针展开，与色带同一口径。
 */
@Composable
fun CycleRing(
    dayIndex: Int,
    cycleLength: Int,
    periodLength: Int,
    size: Dp,
    showCaption: Boolean,
    colors: CycleVisualColors,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val total = cycleLength.coerceAtLeast(1)
    val periodDays = periodLength.coerceIn(0, total)
    val current = dayIndex.coerceIn(1, total)
    val strokeWidth = if (showCaption) 8.dp else 9.dp
    // 200% 字体下环内的 8sp 小字会撑出圆环；外侧阶段标题已经写了「周期第 N 天」，这里只留数字。
    val captionVisible = showCaption && LocalDensity.current.fontScale <= 1.3f

    Box(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val radius = min(this.size.width, this.size.height) / 2f - stroke / 2f - 2.dp.toPx()
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawArc(
                color = colors.track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (periodDays > 0) {
                drawArc(
                    color = colors.period,
                    startAngle = -89f,
                    sweepAngle = (periodDays.toFloat() / total * 360f).coerceAtMost(358f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            // 预计段：周期末尾的 3 天，用虚线区别于已发生的实色弧。
            val predictedSweep = (3f / total * 360f).coerceAtMost(60f)
            drawArc(
                color = colors.period.copy(alpha = 0.75f),
                startAngle = -90f + (total - 3f) / total * 360f,
                sweepAngle = predictedSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = stroke * 0.7f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()), 0f)
                )
            )

            // 今天定位点：落在当天的角度中点，外面描一圈背景色避免与弧糊在一起。
            val angle = Math.toRadians(((current - 0.5f) / total * 360f - 90f).toDouble())
            val dot = Offset(
                (center.x + radius * cos(angle)).toFloat(),
                (center.y + radius * sin(angle)).toFloat()
            )
            drawCircle(colors.background, radius = (if (showCaption) 6.6f else 7f).dp.toPx(), center = dot)
            drawCircle(colors.primary, radius = (if (showCaption) 4.6f else 5f).dp.toPx(), center = dot)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text(
                text = current.toString(),
                fontSize = if (showCaption) 21.sp else 19.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center
            )
            if (captionVisible) {
                androidx.compose.material3.Text(
                    text = "周期第 $current 天",
                    fontSize = 8.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 整周期连续色带：一天一段，经期实色不断开，今天用 2dp 环 + 顶部三角指示，
 * 预计段用虚线填充。与日历的连续色带是同一套语义，不靠颜色单独表达状态。
 */
@Composable
fun CycleBand(
    dayIndex: Int,
    cycleLength: Int,
    periodLength: Int,
    recordedDays: Int,
    colors: CycleVisualColors,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val total = cycleLength.coerceAtLeast(1)
    val periodDays = periodLength.coerceIn(0, total)
    val current = dayIndex.coerceIn(1, total)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val gap = 2.dp.toPx()
        val bandTop = 6.dp.toPx()
        val bandHeight = 18.dp.toPx()
        val segWidth = (this.size.width - gap * (total - 1)) / total
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)

        for (i in 0 until total) {
            val left = i * (segWidth + gap)
            val rectTopLeft = Offset(left, bandTop)
            val rectSize = Size(segWidth, bandHeight)
            val isPredicted = i >= total - 3

            drawRoundRect(
                color = colors.track,
                topLeft = rectTopLeft,
                size = rectSize,
                cornerRadius = corner
            )
            if (i < periodDays) {
                drawRoundRect(
                    color = colors.period.copy(alpha = if (i < recordedDays) 1f else 0.45f),
                    topLeft = rectTopLeft,
                    size = rectSize,
                    cornerRadius = corner
                )
            } else if (isPredicted) {
                drawRoundRect(
                    color = colors.period.copy(alpha = 0.14f),
                    topLeft = rectTopLeft,
                    size = rectSize,
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = colors.period.copy(alpha = 0.8f),
                    topLeft = rectTopLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dash)
                )
            }

            if (i == current - 1) {
                drawRoundRect(
                    color = colors.primary,
                    topLeft = Offset(left + 1.dp.toPx(), bandTop + 1.dp.toPx()),
                    size = Size(segWidth - 2.dp.toPx(), bandHeight - 2.dp.toPx()),
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx())
                )
                val cx = left + segWidth / 2f
                val triangle = Path().apply {
                    moveTo(cx - 4.dp.toPx(), bandTop - 1.dp.toPx())
                    lineTo(cx + 4.dp.toPx(), bandTop - 1.dp.toPx())
                    lineTo(cx, bandTop - 6.dp.toPx())
                    close()
                }
                drawPath(triangle, colors.primary)
            }
        }
    }
}
