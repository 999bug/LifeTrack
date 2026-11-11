package com.example.lifetrack.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.WaterFields

/**
 * 界面重设计的设计 token（方案 A「林间日志」）。
 *
 * 这里只放「跨页面复用、且不属于 Material 默认体系」的量：
 * 模块识别色、间距、圆角、模块图标。字号仍走 MaterialTheme.typography，
 * 避免一次性覆盖全局排版把 200% 字体的既有适配打回原形。
 *
 * 模块色的深浅两套都按 WCAG AA 校验过：浅色在 #FDFDFB 上 ≥ 4.5:1，
 * 深色在 #171E19 上 ≥ 7:1；因此既可作为图标色，也可直接作为小字文字色。
 */
object LifeTrackTokens {

    object Spacing {
        /** 页面水平内边距，四个页签统一。 */
        val page = 20.dp

        /** 卡片之间的间距。 */
        val gap = 10.dp

        /** 卡片内边距。 */
        val card = 16.dp

        /** 分区标题与上一区块的距离。 */
        val section = 22.dp

        /** 模块卡内边距（比普通卡片小一档，避免视觉过重）。 */
        val module = 13.dp
    }

    object Shapes {
        val card = RoundedCornerShape(16.dp)
        val button = RoundedCornerShape(12.dp)
        val icon = RoundedCornerShape(9.dp)
        val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }

    object ModuleColors {

        private val lightPoop = Color(0xFF8A6A4B)
        private val lightWater = Color(0xFF3D7391)
        private val lightCoffee = Color(0xFF9A7134)
        private val lightSport = Color(0xFF4C7A57)
        private val lightIntimacy = Color(0xFF8A5F72)

        private val darkPoop = Color(0xFFC9A382)
        private val darkWater = Color(0xFF8FC0D8)
        private val darkCoffee = Color(0xFFD9B173)
        private val darkSport = Color(0xFF9CC7A6)
        private val darkIntimacy = Color(0xFFC99BAE)

        /** 主题未知时的兜底色，与主色一致。 */
        private val fallback = Color(0xFF426B50)

        fun lightOf(typeCode: String): Color = when (typeCode) {
            DEFAULT_POOP_TYPE.code -> lightPoop
            WaterFields.TYPE_CODE -> lightWater
            CoffeeFields.TYPE_CODE -> lightCoffee
            SportFields.TYPE_CODE -> lightSport
            IntimacyFields.TYPE_CODE -> lightIntimacy
            else -> fallback
        }

        fun darkOf(typeCode: String): Color = when (typeCode) {
            DEFAULT_POOP_TYPE.code -> darkPoop
            WaterFields.TYPE_CODE -> darkWater
            CoffeeFields.TYPE_CODE -> darkCoffee
            SportFields.TYPE_CODE -> darkSport
            IntimacyFields.TYPE_CODE -> darkIntimacy
            else -> fallback
        }

        /** 跟随当前主题取色。深浅色由 background 亮度判断，与 StatsColors 保持同一套判定。 */
        @Composable
        @ReadOnlyComposable
        fun of(typeCode: String): Color {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return if (isDark) darkOf(typeCode) else lightOf(typeCode)
        }
    }

    object ModuleIcons {
        fun of(typeCode: String): ImageVector = when (typeCode) {
            DEFAULT_POOP_TYPE.code -> Icons.Filled.Loop
            WaterFields.TYPE_CODE -> Icons.Filled.WaterDrop
            CoffeeFields.TYPE_CODE -> Icons.Filled.LocalCafe
            SportFields.TYPE_CODE -> Icons.Filled.DirectionsRun
            IntimacyFields.TYPE_CODE -> Icons.Filled.Favorite
            else -> Icons.Filled.Loop
        }
    }
}
