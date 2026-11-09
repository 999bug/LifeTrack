package com.example.lifetrack.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 只覆盖 4 个角色时，M3 的 primaryContainer / secondaryContainer / surfaceContainer* 会退回默认的紫色，
 * 弹窗、FilterChip、Switch、Card 就会和薄荷绿主色打架。这里把会用到的角色一并补齐。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF426B50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3E8CC),
    onPrimaryContainer = Color(0xFF0B2013),
    secondary = Color(0xFF687C68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9DA),
    onSecondaryContainer = Color(0xFF15251A),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFE9EEE7),
    onSurfaceVariant = Color(0xFF414942),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4EF),
    surfaceContainer = Color(0xFFEBEFE9),
    surfaceContainerHigh = Color(0xFFE4E9E2),
    surfaceContainerHighest = Color(0xFFDDE3DB),
    outline = Color(0xFF71796F),
    outlineVariant = Color(0xFFC1C9BE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D6AE),
    onPrimary = Color(0xFF0E1510),
    primaryContainer = Color(0xFF2C4A34),
    onPrimaryContainer = Color(0xFFC3E8CC),
    secondary = Color(0xFFB9CCB8),
    onSecondary = Color(0xFF101A12),
    secondaryContainer = Color(0xFF33463A),
    onSecondaryContainer = Color(0xFFDCE9DA),
    background = Color(0xFF111713),
    onBackground = Color(0xFFE8F2E9),
    surface = Color(0xFF19211B),
    onSurface = Color(0xFFE8F2E9),
    surfaceVariant = Color(0xFF2A352D),
    onSurfaceVariant = Color(0xFFB9C6BA),
    surfaceContainerLowest = Color(0xFF0C110E),
    surfaceContainerLow = Color(0xFF141B16),
    surfaceContainer = Color(0xFF19211B),
    surfaceContainerHigh = Color(0xFF202A23),
    surfaceContainerHighest = Color(0xFF28332B),
    outline = Color(0xFF7E8A80),
    outlineVariant = Color(0xFF3B463D)
)

@Composable
fun LifeTrackTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
