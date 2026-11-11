package com.example.lifetrack.feature.statistics

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * 统计页与历史页月历共用的暗夜仪表盘色板。
 * 从 StatisticsScreen 抽出来，避免两个页面各写一套导致深/浅色不一致。
 */
internal val CardShape = RoundedCornerShape(16.dp)
internal val MetricShape = RoundedCornerShape(13.dp)

internal data class StatsColors(
    val accent: Color,
    val onAccent: Color,
    val barIdle: Color,
    val grid: Color,
    val label: Color,
    val surface: Color,
    val border: Color
)

@Composable
internal fun rememberStatsColors(): StatsColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return remember(scheme, isDark) {
        if (isDark) {
            StatsColors(
                accent = scheme.primary,
                onAccent = Color(0xFF0E1510),
                barIdle = scheme.primary.copy(alpha = 0.38f),
                grid = Color.White.copy(alpha = 0.07f),
                label = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                surface = scheme.surface,
                border = Color.White.copy(alpha = 0.08f)
            )
        } else {
            StatsColors(
                accent = scheme.primary,
                onAccent = scheme.onPrimary,
                barIdle = scheme.primary.copy(alpha = 0.32f),
                grid = scheme.onSurface.copy(alpha = 0.08f),
                label = scheme.onSurfaceVariant,
                surface = scheme.surface,
                border = scheme.onSurface.copy(alpha = 0.10f)
            )
        }
    }
}
