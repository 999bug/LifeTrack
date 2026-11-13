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
import com.example.lifetrack.core.model.MenstruationFields
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

/* ============================================================================
 * 界面定稿 v2 · 批 2：5 类语义化图示
 *
 * 旧版流量 / 痛经 / 分泌物共用同一组「递增竖条」，形状一样，只能靠文字区分。
 * 现在每类字段有自己的形状语言：液滴数量 / 脉冲波纹 / 横条密度 / 表情嘴形 / 3×3 点阵。
 * 选项与已填值都走 [PeriodFieldGlyph]，保证「选的和显示的一样」。
 * ========================================================================== */

/** 流量：level 个逐渐变大的液滴；level=0 画一个淡显小滴表示「无 / 点滴」。 */
@Composable
fun PeriodFlowDrops(
    level: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val n = total.coerceAtLeast(1)
    val filled = level.coerceIn(0, n)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val count = if (filled == 0) 1 else filled
        val cell = w / (n + 1f)
        for (i in 0 until count) {
            val scale = 0.26f + 0.1f * i
            val dropH = h * scale * 2.1f
            val dropW = dropH * 0.72f
            val cx = cell * (i + 1f) + cell * (n - count) / 2f
            val top = h - dropH
            val path = Path().apply {
                moveTo(cx, top)
                cubicTo(cx + dropW * 0.8f, top + dropH * 0.45f, cx + dropW * 0.55f, h, cx, h)
                cubicTo(cx - dropW * 0.55f, h, cx - dropW * 0.8f, top + dropH * 0.45f, cx, top)
                close()
            }
            drawPath(path = path, color = if (filled == 0) color.copy(alpha = 0.22f) else color)
        }
    }
}

/** 痛经：中心圆 + level 圈向外扩散的脉冲波纹，圈数越多表示越强。 */
@Composable
fun PeriodPainPulse(
    level: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val n = total.coerceAtLeast(1)
    val filled = level.coerceIn(0, n)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h * 0.42f
        drawCircle(color, radius = min(w, h) * 0.11f, center = Offset(cx, cy))
        val step = min(w, h) * 0.17f
        // level=0（未填）也留一圈淡显波纹，形状上仍能与「单个圆点」区分开。
        val rings = if (filled == 0) 1 else filled
        for (i in 1..rings) {
            val r = step * i
            drawArc(
                color = color.copy(
                    alpha = if (filled == 0) 0.22f else (0.9f - 0.13f * i).coerceAtLeast(0.3f)
                ),
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = min(w, h) * 0.065f, cap = StrokeCap.Round)
            )
        }
    }
}

/** 分泌物：level 根长短递增的横条，未达到的档位淡显，形状上与竖条 / 液滴明显不同。 */
@Composable
fun PeriodDischargeBars(
    level: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val n = total.coerceAtLeast(1)
    val filled = level.coerceIn(0, n)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val rowH = h / (n + 0.6f)
        for (i in 0 until n) {
            val barW = w * (0.34f + 0.15f * i)
            drawRoundRect(
                color = if (i < filled) color else color.copy(alpha = 0.2f),
                topLeft = Offset((w - barW) / 2f, rowH * (i + 0.5f)),
                size = Size(barW, rowH * 0.42f),
                cornerRadius = CornerRadius(rowH * 0.21f, rowH * 0.21f)
            )
        }
    }
}

/** 症状：3 × 3 点阵，实心点数随 level 增加，与单个圆点的「已记录」标记区分开。 */
@Composable
fun PeriodSymptomGrid(
    level: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val n = total.coerceAtLeast(1)
    val filled = level.coerceIn(0, n)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val step = min(w, h) / 4f
        val solid = (filled.toFloat() / n * 9f).toInt()
        for (i in 0 until 9) {
            val x = step * (i % 3 + 1)
            val y = step * (i / 3 + 1)
            val on = i < solid
            drawCircle(
                color = if (on) color else color.copy(alpha = 0.22f),
                radius = step * (if (on) 0.28f else 0.14f),
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 字段 → 图示的唯一分发点。
 *
 * [level] 是 1-based 的档位（0 = 未填 / 无）；心情按 [MenstruationFields.MOOD_VALUES]
 * 的顺序映射到 [PeriodMoodFace] 的表情索引，症状按已选数量映射到点阵密度。
 */
@Composable
fun PeriodFieldGlyph(
    field: com.example.lifetrack.core.model.PeriodField,
    level: Int,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    symptomLevel: Int = level
) {
    when (field) {
        com.example.lifetrack.core.model.PeriodField.FLOW ->
            PeriodFlowDrops(level, MenstruationFields.FLOW_VALUES.size, color, modifier, size)

        com.example.lifetrack.core.model.PeriodField.PAIN ->
            PeriodPainPulse(level, MenstruationFields.PAIN_VALUES.size, color, modifier, size)

        com.example.lifetrack.core.model.PeriodField.DISCHARGE ->
            PeriodDischargeBars(level, MenstruationFields.DISCHARGE_VALUES.size, color, modifier, size)

        com.example.lifetrack.core.model.PeriodField.MOOD ->
            PeriodMoodFace((level - 1).coerceIn(0, MenstruationFields.MOOD_VALUES.lastIndex), color, modifier, size)

        com.example.lifetrack.core.model.PeriodField.SYMPTOMS ->
            PeriodSymptomGrid(symptomLevel, 4, color, modifier, size)

        com.example.lifetrack.core.model.PeriodField.DIARY ->
            PeriodDiary(color, modifier, size)
    }
}