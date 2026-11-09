package com.example.lifetrack.core.model

import java.time.LocalDate

/** 月历的一格。[date] 为 null 表示前后补白，不参与计数也不响应点击。 */
data class CalendarCell(
    val date: LocalDate?,
    val count: Int
)

/**
 * 一个自然月的 6×7 网格，固定周一为首日，与 statsWindow 的周口径一致。
 * [start] 为该月 1 号，[endExclusive] 为下月 1 号；两者都不含补白日。
 */
data class CalendarMonth(
    val year: Int,
    val month: Int,
    val start: LocalDate,
    val endExclusive: LocalDate,
    val cells: List<CalendarCell>
) {
    val last: LocalDate get() = endExclusive.minusDays(1)

    val label: String get() = "${year}年${month}月"

    /** 补上按天计数。补白格恒为 0，区间外的日期直接忽略。 */
    fun withCounts(countByDate: Map<String, Int>): CalendarMonth =
        copy(
            cells = cells.map { cell ->
                val date = cell.date ?: return@map cell
                cell.copy(count = countByDate[date.toString()] ?: 0)
            }
        )

    companion object {
        /**
         * [anchor] 固定用「今天」，[offset] 是绝对偏移（0 = 本月，-1 = 上月），
         * 因此来回翻页不会累积漂移；正数偏移视为翻到未来，直接拒绝。
         */
        fun of(anchor: LocalDate, offset: Int): CalendarMonth {
            require(offset <= 0) { "calendar cannot be shifted into the future" }
            val first = anchor.withDayOfMonth(1).plusMonths(offset.toLong())
            val endExclusive = first.plusMonths(1)
            val leading = (first.dayOfWeek.value - 1 + 7) % 7
            val gridStart = first.minusDays(leading.toLong())
            val cells = List(42) { index ->
                val date = gridStart.plusDays(index.toLong())
                if (date.isBefore(first) || !date.isBefore(endExclusive)) {
                    CalendarCell(null, 0)
                } else {
                    CalendarCell(date, 0)
                }
            }
            return CalendarMonth(first.year, first.monthValue, first, endExclusive, cells)
        }
    }
}
