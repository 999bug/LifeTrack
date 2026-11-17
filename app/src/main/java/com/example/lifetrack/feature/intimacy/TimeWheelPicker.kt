package com.example.lifetrack.feature.intimacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.ui.WheelColumn
import java.util.Locale

/**
 * 时 / 分双列滚轮（参考界面「时间」一栏）。
 *
 * 滚轮本体在 [WheelColumn]；这里只负责「:」分隔与两列的排版。
 */
@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    accent: Color,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hours = remember { (0..23).map { String.format(Locale.CHINA, "%02d", it) } }
    val minutes = remember { (0..59).map { String.format(Locale.CHINA, "%02d", it) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WheelColumn(
            items = hours,
            selectedIndex = hour,
            label = "小时",
            accent = accent,
            onSelect = onHourChange,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ":",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center
        )
        WheelColumn(
            items = minutes,
            selectedIndex = minute,
            label = "分钟",
            accent = accent,
            onSelect = onMinuteChange,
            modifier = Modifier.weight(1f)
        )
    }
}
