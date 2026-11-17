package com.example.lifetrack.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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

private const val VISIBLE_ROWS = 5

/**
 * 单列吸附滚轮（时间滚轮与经期结束日期滚轮共用）。
 *
 * 实现要点：
 * 1. 用 [LazyColumn] + 吸附（snap）而不是自绘 wheel，滚动、惯性、无障碍都交给框架；
 * 2. 上下各留 2 行 `contentPadding`，让首尾项也能停到中线，不会出现「最后一项滚不到」；
 * 3. 只有滚动**停下**才回写 [onSelect]，避免滚动过程中每一帧都触发上层重组与落库；
 * 4. 行高按 `fontScale` 分档：滚轮必须等行高才能吸附，所以不能用 `heightIn` 自适应，
 *    200% 字体下把行高抬到 52dp，避免大号数字被 42dp 的行裁掉。
 *
 * [items] 直接给成品文案（「09」「2026年」「13日」），列宽与字号由调用方排版；
 * 值变化时本组件只负责把列表滚到对应下标，不持有业务状态。
 */
@Composable
fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    label: String,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lastIndex = items.lastIndex.coerceAtLeast(0)
    val rowHeight = if (LocalDensity.current.fontScale > 1.3f) 52.dp else 42.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, lastIndex)
    )
    val centered by remember(items, rowPx) {
        derivedStateOf {
            val raw = state.firstVisibleItemIndex * rowPx + state.firstVisibleItemScrollOffset
            (raw / rowPx).roundToInt().coerceIn(0, items.lastIndex.coerceAtLeast(0))
        }
    }

    // 只在停下后回写：滚动中的中间值对上层没有意义，还会让上层反复重组。
    LaunchedEffect(state, items, rowPx) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) onSelect(centered)
        }
    }

    // 外部值变化（点行、切换编辑对象、上层夹取日期）时同步位置；正在滚动时不动，避免抢手势。
    LaunchedEffect(selectedIndex, items) {
        val target = selectedIndex.coerceIn(0, lastIndex)
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
                stateDescription = items.getOrNull(selectedIndex) ?: ""
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
                    text = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) }
                        )
                        .padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
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
