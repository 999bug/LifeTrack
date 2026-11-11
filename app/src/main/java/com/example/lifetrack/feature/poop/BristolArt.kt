package com.example.lifetrack.feature.poop

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
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 布里斯托 1–7 型的形态图形，用 Canvas 自绘而不是七份 drawable。
 *
 * 颜色由调用方从主题取，浅深色自动跟随；[creaseColor] 是卡片底色，用来在 3 型的实心长条上
 * 画出裂痕。坐标系沿用设计原型的 64×40 viewBox，等比居中，图形不随系统字号放大。
 */
@Composable
fun BristolArt(
    number: Int,
    color: Color,
    creaseColor: Color,
    modifier: Modifier = Modifier.size(58.dp, 38.dp)
) {
    Canvas(modifier = modifier) {
        drawBristolShape(number, color, creaseColor)
    }
}

private fun DrawScope.drawBristolShape(number: Int, color: Color, creaseColor: Color) {
    val scale = min(size.width / 64f, size.height / 40f)
    val offsetX = (size.width - 64f * scale) / 2f
    val offsetY = (size.height - 40f * scale) / 2f

    fun p(x: Float, y: Float) = Offset(offsetX + x * scale, offsetY + y * scale)
    fun stroke(width: Float) = Stroke(
        width = width * scale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    fun quad(path: Path, cx: Float, cy: Float, ex: Float, ey: Float) {
        val control = p(cx, cy)
        val end = p(ex, ey)
        path.quadraticTo(control.x, control.y, end.x, end.y)
    }
    fun polyline(points: List<Pair<Float, Float>>, width: Float, paint: Color) {
        if (points.isEmpty()) return
        val path = Path().apply {
            val first = p(points.first().first, points.first().second)
            moveTo(first.x, first.y)
            points.drop(1).forEach { (x, y) ->
                val point = p(x, y)
                lineTo(point.x, point.y)
            }
        }
        drawPath(path, paint, style = stroke(width))
    }
    fun roundedBlock(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        drawRoundRect(
            color = color,
            topLeft = p(x, y),
            size = Size(width * scale, height * scale),
            cornerRadius = CornerRadius(radius * scale)
        )
    }

    when (number) {
        1 -> listOf(
            17f to 14f, 32f to 11f, 47f to 15f,
            19f to 28f, 35f to 29f, 49f to 29f
        ).forEach { (x, y) -> drawCircle(color, radius = 6.5f * scale, center = p(x, y)) }

        2 -> listOf(
            Triple(15f, 21f, 9f), Triple(28f, 17f, 10f),
            Triple(41f, 22f, 9f), Triple(52f, 18f, 8f)
        ).forEach { (x, y, radius) ->
            drawCircle(color, radius = radius * scale, center = p(x, y))
        }

        3 -> {
            roundedBlock(6f, 11f, 52f, 18f, 9f)
            polyline(listOf(20f to 12f, 22f to 19f, 20f to 25f), 2.2f, creaseColor)
            polyline(listOf(34f to 12f, 31f to 20f, 33f to 25f), 2.2f, creaseColor)
            polyline(listOf(47f to 12f, 45f to 18f, 47f to 23f), 2.2f, creaseColor)
        }

        4 -> {
            val path = Path().apply {
                val start = p(9f, 25f)
                moveTo(start.x, start.y)
                quad(this, 32f, 7f, 55f, 24f)
            }
            drawPath(path, color, style = Stroke(width = 14f * scale, cap = StrokeCap.Round))
        }

        5 -> {
            roundedBlock(7f, 12f, 18f, 13f, 6.5f)
            roundedBlock(28f, 16f, 20f, 13f, 6.5f)
            roundedBlock(50f, 11f, 11f, 12f, 5.5f)
            roundedBlock(21f, 29f, 17f, 10f, 5f)
        }

        6 -> {
            val path = Path().apply {
                val start = p(8f, 27f)
                moveTo(start.x, start.y)
                quad(this, 13f, 15f, 20f, 17f)
                quad(this, 23f, 6f, 32f, 10f)
                quad(this, 39f, 1f, 45f, 9f)
                quad(this, 56f, 8f, 55f, 18f)
                quad(this, 62f, 25f, 53f, 30f)
                quad(this, 46f, 38f, 37f, 35f)
                quad(this, 28f, 42f, 19f, 37f)
                quad(this, 8f, 38f, 8f, 27f)
                close()
            }
            drawPath(path, color)
        }

        7 -> {
            val first = Path().apply {
                val start = p(7f, 12f)
                moveTo(start.x, start.y)
                quad(this, 14f, 7f, 21f, 12f)
                quad(this, 28f, 17f, 35f, 12f)
                quad(this, 42f, 7f, 49f, 12f)
                quad(this, 56f, 17f, 63f, 12f)
            }
            val second = Path().apply {
                val start = p(10f, 23f)
                moveTo(start.x, start.y)
                quad(this, 17f, 18f, 24f, 23f)
                quad(this, 31f, 28f, 38f, 23f)
                quad(this, 45f, 18f, 52f, 23f)
            }
            val third = Path().apply {
                val start = p(7f, 33f)
                moveTo(start.x, start.y)
                quad(this, 32f, 40f, 57f, 33f)
            }
            listOf(first, second, third).forEach { path ->
                drawPath(path, color, style = stroke(3f))
            }
        }

        else -> Unit
    }
}
