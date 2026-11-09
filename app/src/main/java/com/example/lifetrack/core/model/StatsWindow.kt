package com.example.lifetrack.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** 统计周期。自然周固定以周一为首日。 */
enum class StatsPeriod { WEEK, MONTH, YEAR }

/**
 * 一个左闭右开的统计区间 [start, endExclusive)。
 * 用右开区间是为了让「上一周期」天然等于 [start - 1 周期, start)。
 */
data class StatsWindow(
    val period: StatsPeriod,
    val start: LocalDate,
    val endExclusive: LocalDate
) {
    val lastDate: LocalDate get() = endExclusive.minusDays(1)

    val dayCount: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()

    /** 上一个同长度周期。 */
    val previous: StatsWindow
        get() = when (period) {
            StatsPeriod.WEEK -> copy(start = start.minusWeeks(1), endExclusive = start)
            StatsPeriod.MONTH -> copy(start = start.minusMonths(1), endExclusive = start)
            StatsPeriod.YEAR -> copy(start = start.minusYears(1), endExclusive = start)
        }

    val label: String
        get() = when (period) {
            StatsPeriod.WEEK ->
                if (start.year == lastDate.year) {
                    "${start.format(MONTH_DAY)} — ${lastDate.format(MONTH_DAY)}"
                } else {
                    // 跨年周用短格式，否则「2025年12月29日 — 2026年1月4日」会撑破顶部胶囊
                    "${start.format(COMPACT_DATE)} — ${lastDate.format(COMPACT_DATE)}"
                }

            StatsPeriod.MONTH -> start.format(YEAR_MONTH)
            StatsPeriod.YEAR -> "${start.year}年"
        }

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    private companion object {
        val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
        val COMPACT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA)
        val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    }
}

/**
 * 以 [today] 为锚点、偏移 [offset] 个周期，构造统计区间。
 * offset 为绝对值（0 = 当前周期，-1 = 上一周期），因此来回翻页不会累积漂移。
 */
fun statsWindow(period: StatsPeriod, offset: Int, today: LocalDate): StatsWindow {
    val anchor = when (period) {
        StatsPeriod.WEEK -> today.plusWeeks(offset.toLong())
        StatsPeriod.MONTH -> today.plusMonths(offset.toLong())
        StatsPeriod.YEAR -> today.plusYears(offset.toLong())
    }
    val start = when (period) {
        StatsPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatsPeriod.MONTH -> anchor.withDayOfMonth(1)
        StatsPeriod.YEAR -> anchor.withDayOfYear(1)
    }
    val endExclusive = when (period) {
        StatsPeriod.WEEK -> start.plusWeeks(1)
        StatsPeriod.MONTH -> start.plusMonths(1)
        StatsPeriod.YEAR -> start.plusYears(1)
    }
    return StatsWindow(period, start, endExclusive)
}
