package com.example.lifetrack.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF426B50),
    onPrimary = Color.White,
    secondary = Color(0xFF687C68),
    background = Color(0xFFF7F8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EEE7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D6AE),
    secondary = Color(0xFFB9CCB8),
    background = Color(0xFF111713),
    surface = Color(0xFF19211B),
    surfaceVariant = Color(0xFF2A352D)
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
