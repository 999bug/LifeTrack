package com.example.lifetrack.feature.intimacy

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 亲密关系「措施」的 10 枚自绘图示。
 *
 * 与经期子应用的其他图示同一套做法：Canvas 现画、线稿为主、不用 emoji / 图片素材 / 渐变；
 * 颜色由调用方按主题或模块色传入。每枚图形的形状语言互不重复，避免「九个一样的图标」：
 * 心形 + 斜杠（无措施）、胶囊 + 环（避孕套）、液滴 + 外箭头（体外排精）、
 * 液滴 + 斜杠（未射精）、圆片 + 闪电（紧急）、药板 + 四点 / 时钟（短效 / 长效）、
 * T 形 + 环（节育环）、盾牌（其他）、心形 + 火花（自慰）。
 *
 * 未知 code 画一个空心圆兜底，绝不因为数据里出现新值就崩。
 */
@Composable
fun IntimacyMethodIcon(
    method: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = Stroke(
            width = min(w, h) * 0.075f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val t = min(w, h) * 0.075f
        when (method) {
            "none" -> {
                drawPath(heartPath(w * 0.06f, h * 0.04f, w * 0.88f, h * 0.88f), color, style = line)
                drawLine(
                    color,
                    Offset(w * 0.20f, h * 0.16f),
                    Offset(w * 0.80f, h * 0.84f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
            }

            "condom" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.32f, h * 0.26f),
                    size = Size(w * 0.36f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
                    style = line
                )
                drawCircle(color, radius = w * 0.055f, center = Offset(w * 0.5f, h * 0.21f))
                drawOval(
                    color = color,
                    topLeft = Offset(w * 0.28f, h * 0.68f),
                    size = Size(w * 0.44f, h * 0.12f),
                    style = line
                )
            }

            "withdrawal" -> {
                dropPath(w * 0.30f, h * 0.40f, w * 0.28f, h * 0.46f).let { drawPath(it, color, style = line) }
                drawLine(
                    color,
                    Offset(w * 0.50f, h * 0.34f),
                    Offset(w * 0.82f, h * 0.34f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(w * 0.70f, h * 0.22f),
                    Offset(w * 0.82f, h * 0.34f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(w * 0.70f, h * 0.46f),
                    Offset(w * 0.82f, h * 0.34f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
            }

            "noEjaculation" -> {
                dropPath(w * 0.5f, h * 0.22f, w * 0.34f, h * 0.58f).let { drawPath(it, color, style = line) }
                drawLine(
                    color,
                    Offset(w * 0.18f, h * 0.16f),
                    Offset(w * 0.82f, h * 0.84f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
            }

            "emergencyPill" -> {
                drawCircle(color, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = line)
                val bolt = Path().apply {
                    moveTo(w * 0.54f, h * 0.28f)
                    lineTo(w * 0.40f, h * 0.52f)
                    lineTo(w * 0.50f, h * 0.52f)
                    lineTo(w * 0.44f, h * 0.74f)
                    lineTo(w * 0.62f, h * 0.46f)
                    lineTo(w * 0.52f, h * 0.46f)
                    close()
                }
                drawPath(bolt, color)
            }

            "shortPill" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.22f, h * 0.26f),
                    size = Size(w * 0.56f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
                    style = line
                )
                listOf(0.38f to 0.42f, 0.62f to 0.42f, 0.38f to 0.58f, 0.62f to 0.58f).forEach { (fx, fy) ->
                    drawCircle(color, radius = w * 0.05f, center = Offset(w * fx, h * fy))
                }
            }

            "longPill" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.20f),
                    size = Size(w * 0.50f, h * 0.40f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = line
                )
                listOf(0.26f to 0.32f, 0.48f to 0.32f, 0.26f to 0.48f, 0.48f to 0.48f).forEach { (fx, fy) ->
                    drawCircle(color, radius = w * 0.045f, center = Offset(w * fx, h * fy))
                }
                drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.74f, h * 0.72f), style = line)
                drawLine(
                    color,
                    Offset(w * 0.74f, h * 0.72f),
                    Offset(w * 0.74f, h * 0.62f),
                    strokeWidth = t * 0.9f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(w * 0.74f, h * 0.72f),
                    Offset(w * 0.82f, h * 0.76f),
                    strokeWidth = t * 0.9f,
                    cap = StrokeCap.Round
                )
            }

            "iud" -> {
                drawLine(
                    color,
                    Offset(w * 0.28f, h * 0.24f),
                    Offset(w * 0.72f, h * 0.24f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(w * 0.5f, h * 0.24f),
                    Offset(w * 0.5f, h * 0.62f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.28f, h * 0.24f))
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.24f))
                drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.73f), style = line)
                drawLine(
                    color,
                    Offset(w * 0.5f, h * 0.84f),
                    Offset(w * 0.42f, h * 0.90f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(w * 0.5f, h * 0.84f),
                    Offset(w * 0.58f, h * 0.90f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
            }

            "other" -> {
                val shield = Path().apply {
                    moveTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.78f, h * 0.28f)
                    lineTo(w * 0.78f, h * 0.52f)
                    cubicTo(w * 0.78f, h * 0.68f, w * 0.62f, h * 0.80f, w * 0.5f, h * 0.86f)
                    cubicTo(w * 0.38f, h * 0.80f, w * 0.22f, h * 0.68f, w * 0.22f, h * 0.52f)
                    lineTo(w * 0.22f, h * 0.28f)
                    close()
                }
                drawPath(shield, color, style = line)
                drawLine(
                    color,
                    Offset(w * 0.5f, h * 0.38f),
                    Offset(w * 0.5f, h * 0.58f),
                    strokeWidth = t,
                    cap = StrokeCap.Round
                )
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.68f))
            }

            "masturbation" -> {
                drawPath(heartPath(w * 0.10f, h * 0.16f, w * 0.72f, h * 0.72f), color, style = line)
                listOf(
                    (w * 0.86f to h * 0.16f) to (w * 0.94f to h * 0.08f),
                    (w * 0.90f to h * 0.28f) to (w * 0.98f to h * 0.26f),
                    (w * 0.78f to h * 0.10f) to (w * 0.80f to h * 0.02f)
                ).forEach { (from, to) ->
                    drawLine(
                        color,
                        Offset(from.first, from.second),
                        Offset(to.first, to.second),
                        strokeWidth = t * 0.85f,
                        cap = StrokeCap.Round
                    )
                }
            }

            else -> drawCircle(color, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = line)
        }
    }
}

/** 心形轮廓：给定外接矩形，底尖在下，凹陷在顶部中间。 */
private fun heartPath(left: Float, top: Float, width: Float, height: Float): Path {
    val w = width
    val h = height
    return Path().apply {
        moveTo(left + w * 0.5f, top + h * 0.86f)
        cubicTo(
            left + w * 0.10f, top + h * 0.58f,
            left + w * 0.14f, top + h * 0.16f,
            left + w * 0.33f, top + h * 0.18f
        )
        cubicTo(
            left + w * 0.43f, top + h * 0.19f,
            left + w * 0.48f, top + h * 0.28f,
            left + w * 0.5f, top + h * 0.34f
        )
        cubicTo(
            left + w * 0.52f, top + h * 0.28f,
            left + w * 0.57f, top + h * 0.19f,
            left + w * 0.67f, top + h * 0.18f
        )
        cubicTo(
            left + w * 0.86f, top + h * 0.16f,
            left + w * 0.90f, top + h * 0.58f,
            left + w * 0.5f, top + h * 0.86f
        )
        close()
    }
}

/** 液滴轮廓：顶部尖角、底部圆腹。 */
private fun DrawScope.dropPath(cx: Float, top: Float, width: Float, height: Float): Path {
    val bottom = top + height
    val half = width / 2f
    return Path().apply {
        moveTo(cx, top)
        cubicTo(cx + half * 1.5f, top + height * 0.45f, cx + half * 1.1f, bottom, cx, bottom)
        cubicTo(cx - half * 1.1f, bottom, cx - half * 1.5f, top + height * 0.45f, cx, top)
        close()
    }
}
