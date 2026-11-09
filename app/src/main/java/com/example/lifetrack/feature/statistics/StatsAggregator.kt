package com.example.lifetrack.feature.statistics

import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.StatsWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** 图表上的一根柱子。[showLabel] 由聚合层决定，避免月视图 31 根柱把轴标挤成一团。 */
data class StatBar(
    val label: String,
    val value: Int,
    val highlighted: Boolean,
    val showLabel: Boolean
)

data class PeriodStats(
    val window: StatsWindow,
    val total: Int,
    val previousTotal: Int,
    val dailyAverage: Double,
    val peakValue: Int,
    val peakDate: LocalDate?,
    val longestStreak: Int,
    val commonBandStartHour: Int?,
    val bars: List<StatBar>,
    val maxBarValue: Int
) {
    val hasData: Boolean get() = total > 0
    val delta: Int get() = total - previousTotal

    /** 完整写法，给洞察文案用。 */
    val commonBand: String?
        get() = commonBandStartHour?.let { String.format(Locale.CHINA, "%02d:00–%02d:00", it, it + 2) }

    /** 紧凑写法，给指标卡用；字体放大后完整写法会被截断。 */
    val commonBandShort: String?
        get() = commonBandStartHour?.let { String.format(Locale.CHINA, "%02d–%02d", it, it + 2) }
}

/**
 * 纯函数聚合：输入一段连续日期的按天计数 + 区间内的时间戳，输出页面要的全部数字。
 * 没有 Android 依赖，可以直接单测。
 */
object StatsAggregator {

    private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

    fun build(
        window: StatsWindow,
        today: LocalDate,
        dailyCounts: List<DailyCountProjection>,
        timestamps: List<EventTimestamp>,
        zoneFallback: ZoneId = ZoneId.systemDefault()
    ): PeriodStats {
        val countByDate = HashMap<String, Int>(dailyCounts.size)
        for (row in dailyCounts) countByDate[row.localDate] = row.count

        val days = ArrayList<LocalDate>(window.dayCount)
        var cursor = window.start
        while (cursor.isBefore(window.endExclusive)) {
            days += cursor
            cursor = cursor.plusDays(1)
        }

        val dailyValues = IntArray(days.size) { index -> countByDate[days[index].toString()] ?: 0 }
        val total = dailyValues.sum()

        val previousStart = window.previous.start.toString()
        val previousEnd = window.previous.endExclusive.toString()
        val previousTotal = dailyCounts
            .filter { it.localDate >= previousStart && it.localDate < previousEnd }
            .sumOf { it.count }

        var peakValue = 0
        var peakDate: LocalDate? = null
        var longestStreak = 0
        var running = 0
        days.forEachIndexed { index, date ->
            val value = dailyValues[index]
            if (value > peakValue) {
                peakValue = value
                peakDate = date
            }
            if (value > 0) {
                running += 1
                if (running > longestStreak) longestStreak = running
            } else {
                running = 0
            }
        }

        val containsToday = window.contains(today)
        val bars = buildBars(window, days, dailyValues, today, containsToday)
        val maxBarValue = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)

        return PeriodStats(
            window = window,
            total = total,
            previousTotal = previousTotal,
            dailyAverage = if (days.isEmpty()) 0.0 else total.toDouble() / days.size,
            peakValue = peakValue,
            peakDate = peakDate,
            longestStreak = longestStreak,
            commonBandStartHour = commonBandStartHour(window, timestamps, zoneFallback),
            bars = bars,
            maxBarValue = maxBarValue
        )
    }

    private fun buildBars(
        window: StatsWindow,
        days: List<LocalDate>,
        dailyValues: IntArray,
        today: LocalDate,
        containsToday: Boolean
    ): List<StatBar> {
        val todayIndex = if (containsToday) days.indexOf(today) else -1
        val fallbackIndex = dailyValues.indices
            .filter { dailyValues[it] > 0 }
            .maxByOrNull { dailyValues[it] }
        val activeIndex = if (todayIndex >= 0) todayIndex else fallbackIndex

        return when (window.period) {
            StatsPeriod.WEEK -> days.mapIndexed { index, date ->
                StatBar(
                    label = WEEK_LABELS[date.dayOfWeek.value - 1],
                    value = dailyValues[index],
                    highlighted = index == activeIndex,
                    showLabel = true
                )
            }

            StatsPeriod.MONTH -> days.mapIndexed { index, date ->
                StatBar(
                    label = date.dayOfMonth.toString(),
                    value = dailyValues[index],
                    highlighted = index == activeIndex,
                    showLabel = date.dayOfMonth == 1 || date.dayOfMonth % 5 == 0
                )
            }

            StatsPeriod.YEAR -> {
                val monthly = IntArray(12)
                days.forEachIndexed { index, date -> monthly[date.monthValue - 1] += dailyValues[index] }
                val activeMonth = if (containsToday) {
                    today.monthValue - 1
                } else {
                    monthly.indices.filter { monthly[it] > 0 }.maxByOrNull { monthly[it] }
                }
                (1..12).map { month ->
                    StatBar(
                        label = month.toString(),
                        value = monthly[month - 1],
                        highlighted = month - 1 == activeMonth,
                        showLabel = true
                    )
                }
            }
        }
    }

    /**
     * 最常见的 2 小时时段。时区取自记录本身，而不是设备当前时区——
     * 记录里存的是当时所在地的 zoneId，跨时区旅行后仍然算得对。
     */
    private fun commonBandStartHour(
        window: StatsWindow,
        timestamps: List<EventTimestamp>,
        zoneFallback: ZoneId
    ): Int? {
        if (timestamps.isEmpty()) return null
        val hourly = IntArray(24)
        for (stamp in timestamps) {
            val zone = runCatching { ZoneId.of(stamp.zoneId) }.getOrDefault(zoneFallback)
            val local = Instant.ofEpochMilli(stamp.occurredAtEpochMillis).atZone(zone)
            if (!window.contains(local.toLocalDate())) continue
            hourly[local.hour] += 1
        }
        var bestStart = -1
        var bestScore = 0
        var bestHead = 0
        for (hour in 0..22) {
            val score = hourly[hour] + hourly[hour + 1]
            val head = hourly[hour]
            // 并列时优先让窗口从真正有记录的那个小时开始，
            // 否则一条 07:30 的记录会被说成「06:00–08:00」。
            if (score > bestScore || (score == bestScore && score > 0 && head > bestHead)) {
                bestScore = score
                bestStart = hour
                bestHead = head
            }
        }
        if (bestScore <= 0) return null
        return bestStart
    }
}
