package com.example.lifetrack.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 界面设计 token（方案 A「林间日志」）。
 * 模块颜色与图标统一委托 [RecordTypeUiRegistry]，这里只保留跨页面布局 token。
 */
object LifeTrackTokens {

    object Spacing {
        val page = 20.dp
        val gap = 10.dp
        val card = 16.dp
        val section = 22.dp
        val module = 13.dp
    }

    object Shapes {
        val card = RoundedCornerShape(16.dp)
        val button = RoundedCornerShape(12.dp)
        val icon = RoundedCornerShape(9.dp)
        val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }

    object ModuleColors {
        fun lightOf(typeCode: String): Color = RecordTypeUiRegistry.colorOf(typeCode, dark = false)

        fun darkOf(typeCode: String): Color = RecordTypeUiRegistry.colorOf(typeCode, dark = true)

        @Composable
        @ReadOnlyComposable
        fun of(typeCode: String): Color {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return RecordTypeUiRegistry.colorOf(typeCode, dark = isDark)
        }
    }

    object ModuleIcons {
        fun of(typeCode: String): ImageVector = RecordTypeUiRegistry.iconOf(typeCode)
    }
}
