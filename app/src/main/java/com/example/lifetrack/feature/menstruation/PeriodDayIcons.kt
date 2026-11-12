package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 经期逐行填写区的自绘矢量图示（设计 §5.5 ③）。
 *
 * 全部用 Canvas 现画，不引用参考 App 的图标 / 素材，不用 emoji、不用渐变。
 * 语义固定：流量 / 痛经 / 分泌物 = n 格等级格，心情 = 简笔表情，症状 = 圆点，
 * 日记 = 纸笔，选中 = 对勾。**选项与已填值调用同一组函数**，保证「选的和显示的一样」。
 *
 * 尺寸固定为 dp，不随字号拉伸（设计 §5.4 视觉约束）；颜色由调用方按主题 / 模块色传入。
 * 图示不承担读屏文案，contentDescription 由旁边的文字负责，这里不重复播报。
 */

/** 等级格：total 根递增竖条，前 filled 根实心、其余淡显；filled=0 表示「无」。 */
@Composable
fun PeriodLevelBars(
    filled: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val n = total.coerceAtLeast(1)
    val solid = filled.coerceIn(0, n)
    Canvas(modifier = modifier.size(size)) {
        val barWidth = this.size.width / (n + (n - 1) * 0.35f)
        val gap = barWidth * 0.35f
        val radius = CornerRadius(barWidth * 0.28f, barWidth * 0.28f)
        for (i in 0 until n) {
            val barHeight = this.size.height * (i + 1) / n
            drawRoundRect(
                color = if (i < solid) color else color.copy(alpha = 0.22f),
                topLeft = Offset(i * (barWidth + gap), this.size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
        }
    }
}

/**
 * 简笔表情：0 平静 / 1 愉快 / 2 敏感 / 3 低落 / 4 易怒。
 * 圆脸 + 双眼，嘴形随情绪变化；易怒额外加两道皱眉。
 */
@Composable
fun PeriodMoodFace(
    index: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val mood = index.coerceIn(0, 4)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = min(w, h) * 0.07f
        val faceRadius = min(w, h) * 0.42f
        val center = Offset(w / 2f, h / 2f)
        drawCircle(
            color = color,
            radius = faceRadius,
            center = center,
            style = Stroke(width = stroke)
        )
        val eyeRadius = min(w, h) * 0.05f
        val eyeY = h * 0.42f
        drawCircle(color, eyeRadius, Offset(w * 0.36f, eyeY))
        drawCircle(color, eyeRadius, Offset(w * 0.64f, eyeY))

        val mouthWidth = w * 0.34f
        val mouthHeight = h * 0.20f
        val mouthLeft = (w - mouthWidth) / 2f
        val mouthTop = h * 0.55f
        val mouthStroke = Stroke(width = stroke, cap = StrokeCap.Round)
        when (mood) {
            1 -> drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(mouthLeft, mouthTop - mouthHeight * 0.5f),
                size = Size(mouthWidth, mouthHeight),
                style = mouthStroke
            )
            3, 4 -> drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(mouthLeft, mouthTop),
                size = Size(mouthWidth, mouthHeight),
                style = mouthStroke
            )
            else -> drawLine(
                color = color,
                start = Offset(mouthLeft, mouthTop + mouthHeight * 0.3f),
                end = Offset(mouthLeft + mouthWidth, mouthTop + mouthHeight * 0.3f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        if (mood == 4) {
            val browY = h * 0.30f
            drawLine(
                color = color,
                start = Offset(w * 0.28f, browY),
                end = Offset(w * 0.44f, browY + h * 0.06f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.72f, browY),
                end = Offset(w * 0.56f, browY + h * 0.06f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/** 圆点标签：症状用，实心圆点。 */
@Composable
fun PeriodSymptomDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        drawCircle(
            color = color,
            radius = min(this.size.width, this.size.height) * 0.26f,
            center = Offset(this.size.width / 2f, this.size.height / 2f)
        )
    }
}

/** 纸笔：日记用，一页纸 + 两条横线 + 一支斜笔。 */
@Composable
fun PeriodDiary(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = min(w, h) * 0.07f
        val pageLeft = w * 0.16f
        val pageTop = h * 0.12f
        val pageWidth = w * 0.52f
        val pageHeight = h * 0.76f
        drawRoundRect(
            color = color,
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageWidth, pageHeight),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(pageLeft + pageWidth * 0.2f, pageTop + pageHeight * 0.3f),
            end = Offset(pageLeft + pageWidth * 0.8f, pageTop + pageHeight * 0.3f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(pageLeft + pageWidth * 0.2f, pageTop + pageHeight * 0.55f),
            end = Offset(pageLeft + pageWidth * 0.8f, pageTop + pageHeight * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.58f, h * 0.82f),
            end = Offset(w * 0.9f, h * 0.28f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** 对勾：选中态用。 */
@Composable
fun PeriodCheck(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.8f, h * 0.28f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = min(w, h) * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}