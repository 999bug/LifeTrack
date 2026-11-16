package com.example.lifetrack.feature.intimacy

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 时 / 分双列滚轮（参考界面「时间」一栏）。
 *
 * 实现要点：
 * 1. 用 [LazyColumn] + 吸附（snap）而不是自绘 wheel，滚动、惯性、无障碍都交给框架；
 * 2. 上下各留 2 行 `contentPadding`，让首尾项也能停到中线，不会出现「23 点滚不到」；
 * 3. 只有滚动**停下**才回写父状态，避免滚动过程中每一帧都触发上层重组与落库；
 * 4. 行高按 `fontScale` 分档：滚轮必须等行高才能吸附，所以不能用 `heightIn` 自适应，
 *    200% 字体下把行高抬到 52dp，避免 22sp 数字被 42dp 的行裁掉。
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Wheel(
            value = hour,
            range = 0..23,
            label = "小时",
            accent = accent,
            onChange = onHourChange,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ":",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center
        )
        Wheel(
            value = minute,
            range = 0..59,
            label = "分钟",
            accent = accent,
            onChange = onMinuteChange,
            modifier = Modifier.weight(1f)
        )
    }
}

private const val VISIBLE_ROWS = 5

@Composable
private fun Wheel(
    value: Int,
    range: IntRange,
    label: String,
    accent: Color,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(range) { range.toList() }
    val rowHeight = if (LocalDensity.current.fontScale > 1.3f) 52.dp else 42.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, items.lastIndex)
    )
    val centered by remember(items, rowPx) {
        derivedStateOf {
            val raw = state.firstVisibleItemIndex * rowPx + state.firstVisibleItemScrollOffset
            (raw / rowPx).roundToInt().coerceIn(0, items.lastIndex)
        }
    }

    // 只在停下后回写：滚动中的中间值对上层没有意义，还会让上层反复重组。
    LaunchedEffect(state, items, rowPx) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) onChange(range.first + centered)
        }
    }

    // 外部值变化（点行、切换编辑对象）时同步位置；正在滚动时不动，避免抢手势。
    LaunchedEffect(value) {
        val target = (value - range.first).coerceIn(0, items.lastIndex)
        if (!state.isScrollInProgress &&
            (state.firstVisibleItemIndex != target || state.firstVisibleItemScrollOffset != 0)
        ) {
            state.scrollToItem(target)
        }
    }

    Box(
        modifier = modifier
            .height(rowHeight * VISIBLE_ROWS)
            .semantics {
                contentDescription = "${label}滚轮"
                stateDescription = String.format(java.util.Locale.CHINA, "%02d", value)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.10f))
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = rowHeight * 2),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
        ) {
            itemsIndexed(items) { index, item ->
                val distance = abs(index - centered)
                val selected = distance == 0
                Text(
                    text = String.format(java.util.Locale.CHINA, "%02d", item),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onChange(item) }
                        )
                        .padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    fontSize = when (distance) {
                        0 -> 23.sp
                        1 -> 17.sp
                        else -> 14.sp
                    },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = when (distance) {
                        0 -> accent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = (0.85f - 0.22f * distance).coerceAtLeast(0.3f)
                        )
                    }
                )
            }
        }
    }
}
