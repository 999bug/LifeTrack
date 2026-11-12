package com.example.lifetrack.core.model

import java.time.LocalDate

/**
 * 经期子应用专用的可翻未来月纯模型（设计 §5.4 / W14）。
 *
 * 与 [CalendarMonth] 各自独立：历史月历的 `of()` 有 `require(offset <= 0)`，只能回看；
 * 经期预测的核心是看未来预计经期，方向相反，因此另写本模型，**不复用也不改** `CalendarMonth`，
 * 既有月历与 `CalendarMonthTest` 保持零 diff。固定周一为首日、6×7 = 42 格，与历史月历口径一致。
 *
 * [cells] 为 42 个格子，`null` 表示前后补白（不属本月、不响应点击）；非 null 即本月日期。
 * 实际 / 预测 / 今天 / 选中等染色状态由视图按日期集合派生，模型只负责网格本身。
 */
data class PeriodMonth(
    val year: Int,
    val month: Int,
    val start: LocalDate,
    val endExclusive: LocalDate,
    val cells: List<LocalDate?>
) {
    val last: LocalDate get() = endExclusive.minusDays(1)

    val label: String get() = "${year}年${month}月"

    companion object {
        /**
         * [anchor] 固定用「今天」，[offset] 是绝对偏移（0 = 本月，-1 = 上月，+1 = 下月），
         * 来回翻页不累积漂移。与历史月历不同，这里**允许正偏移翻到未来**。
         */
        fun of(anchor: LocalDate, offset: Int): PeriodMonth {
            val first = anchor.withDayOfMonth(1).plusMonths(offset.toLong())
            val endExclusive = first.plusMonths(1)
            val leading = (first.dayOfWeek.value - 1 + 7) % 7
            val gridStart = first.minusDays(leading.toLong())
            val cells = List(42) { index ->
                val date = gridStart.plusDays(index.toLong())
                if (date.isBefore(first) || !date.isBefore(endExclusive)) null else date
            }
            return PeriodMonth(first.year, first.monthValue, first, endExclusive, cells)
        }
    }
}