package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.core.ui.WheelColumn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 「选择经期结束日期」滚轮（参考 App 的经期结束弹层）。
 *
 * 为什么要有它：点「大姨妈来了」之后每天自动延续，忘了点「大姨妈走了」就会一路记到今天，
 * 结束日只能往后跑、改不回来。这里把结束日做成可直接滚动的年 / 月 / 日三列，
 * 确定后由 [com.example.lifetrack.feature.home.MainViewModel.changePeriodEnd] 负责补写与撤销。
 *
 * 交互约束：
 * - 只能选 `[earliest..latest]` 之间，即「本段首日」到今天（已结束的段只额外放宽
 *   [com.example.lifetrack.core.model.PeriodFill.MAX_GAP_DAYS] 天，避免误触补齐一大段）；
 * - 年 / 月 / 日的可选项跟着上一级联动，不会滚出非法日期，也不需要提交后报错；
 * - 标题下方实时显示「首日 – 选定日 · 共 N 天」，滚的时候就能看到结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodEndDatePicker(
    earliest: LocalDate,
    latest: LocalDate,
    initialEnd: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val accent = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val first = minOf(earliest, latest)
    val last = maxOf(earliest, latest)
    var selected by remember(initialEnd, first, last) {
        mutableStateOf(clampToRange(initialEnd, first, last))
    }

    val years = remember(first, last) { (first.year..last.year).toList() }
    val months = remember(selected.year, first, last) {
        val from = if (selected.year == first.year) first.monthValue else 1
        val to = if (selected.year == last.year) last.monthValue else 12
        (from..to).toList()
    }
    val days = remember(selected.year, selected.monthValue, first, last) {
        val month = YearMonth.of(selected.year, selected.monthValue)
        val from = if (month == YearMonth.from(first)) first.dayOfMonth else 1
        val to = if (month == YearMonth.from(last)) last.dayOfMonth else month.lengthOfMonth()
        (from..to).toList()
    }
    val lengthDays = ChronoUnit.DAYS.between(first, selected).toInt() + 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Text(
                    text = "选择经期结束日期",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onConfirm(selected) }) {
                    Text("确定", color = accent)
                }
            }

            Text(
                text = "${cnDate(first)} – ${cnDate(selected)} · 共 $lengthDays 天",
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            Text(
                text = if (last == today) {
                    "结束日不能晚于今天；改早了，结束日之后自动记录的日子会一起撤掉（填过内容的保留）。"
                } else {
                    "结束日最早为本段首日 ${cnDate(first)}。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WheelColumn(
                    items = remember(years) { years.map { "${it}年" } },
                    selectedIndex = years.indexOf(selected.year).coerceAtLeast(0),
                    label = "年",
                    accent = accent,
                    onSelect = { index ->
                        selected = clampToRange(
                            safeDate(years[index], selected.monthValue, selected.dayOfMonth),
                            first,
                            last
                        )
                    },
                    modifier = Modifier.weight(1.25f)
                )
                WheelColumn(
                    items = remember(months) { months.map { "${it}月" } },
                    selectedIndex = months.indexOf(selected.monthValue).coerceAtLeast(0),
                    label = "月",
                    accent = accent,
                    onSelect = { index ->
                        selected = clampToRange(
                            safeDate(selected.year, months[index], selected.dayOfMonth),
                            first,
                            last
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                WheelColumn(
                    items = remember(days) { days.map { "${it}日" } },
                    selectedIndex = days.indexOf(selected.dayOfMonth).coerceAtLeast(0),
                    label = "日",
                    accent = accent,
                    onSelect = { index ->
                        selected = LocalDate.of(selected.year, selected.monthValue, days[index])
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun clampToRange(date: LocalDate, first: LocalDate, last: LocalDate): LocalDate = when {
    date.isBefore(first) -> first
    date.isAfter(last) -> last
    else -> date
}

/** 换年 / 换月时把日夹到当月长度，避免 2 月 31 日这种非法组合。 */
private fun safeDate(year: Int, month: Int, day: Int): LocalDate =
    LocalDate.of(year, month, day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))

private fun cnDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"
