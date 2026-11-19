package com.example.lifetrack.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.TeaFields
import com.example.lifetrack.core.model.WaterFields

/** 单一记录类型的 UI 贡献；颜色、图标和类型码不再散落在各页面。 */
data class RecordTypeUiDefinition(
    val icon: ImageVector,
    val lightColor: Color,
    val darkColor: Color
)

object RecordTypeUiRegistry {
    val fallback = RecordTypeUiDefinition(
        icon = Icons.Filled.Loop,
        lightColor = Color(0xFF426B50),
        darkColor = Color(0xFF426B50)
    )

    val builtIn: Map<String, RecordTypeUiDefinition> = mapOf(
        PoopFields.TYPE_CODE to RecordTypeUiDefinition(
            Icons.Filled.Loop,
            Color(0xFF8A6A4B),
            Color(0xFFC9A382)
        ),
        WaterFields.TYPE_CODE to RecordTypeUiDefinition(
            Icons.Filled.WaterDrop,
            Color(0xFF3D7391),
            Color(0xFF8FC0D8)
        ),
        CoffeeFields.TYPE_CODE to RecordTypeUiDefinition(
            Icons.Filled.LocalCafe,
            Color(0xFF9A7134),
            Color(0xFFD9B173)
        ),
        TeaFields.TYPE_CODE to RecordTypeUiDefinition(
            Icons.Filled.Spa,
            Color(0xFF6E8B4E),
            Color(0xFFB7D09A)
        ),
        SportFields.TYPE_CODE to RecordTypeUiDefinition(
            Icons.AutoMirrored.Filled.DirectionsRun,
            Color(0xFF4C7A57),
            Color(0xFF9CC7A6)
        ),
        IntimacyFields.TYPE_CODE to RecordTypeUiDefinition(
            Icons.Filled.Favorite,
            Color(0xFF8A5F72),
            Color(0xFFC99BAE)
        ),
        MenstruationFields.TYPE_CODE to RecordTypeUiDefinition(
            Icons.Filled.Bloodtype,
            Color(0xFFA35B52),
            Color(0xFFE0A79B)
        )
    )

    fun has(typeCode: String): Boolean = typeCode in builtIn

    fun iconOf(typeCode: String): ImageVector = builtIn[typeCode]?.icon ?: fallback.icon

    fun colorOf(typeCode: String, dark: Boolean): Color =
        builtIn[typeCode]?.let { if (dark) it.darkColor else it.lightColor } ?: fallback.lightColor
}
