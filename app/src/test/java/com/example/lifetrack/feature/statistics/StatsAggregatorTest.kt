package com.example.lifetrack.feature.statistics

import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.StatDetailProjection
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.statsWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsAggregatorTest {

    /** 2026-11-09 是周一。 */
    private val today: LocalDate = LocalDate.of(2026, 11, 9)

    @Test
    fun `week window starts on monday`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, LocalDate.of(2026, 11, 11))
        assertEquals(LocalDate.of(2026, 11, 9), window.start)
        assertEquals(LocalDate.of(2026, 11, 15), window.lastDate)
        assertEquals(7, window.dayCount)
    }

    @Test
    fun `month window handles leap february`() {
        val window = statsWindow(StatsPeriod.MONTH, 0, LocalDate.of(2024, 2, 10))
        assertEquals(LocalDate.of(2024, 2, 1), window.start)
        assertEquals(LocalDate.of(2024, 2, 29), window.lastDate)
        assertEquals(29, window.dayCount)
    }

    @Test
    fun `previous month crosses year boundary`() {
        val window = statsWindow(StatsPeriod.MONTH, 0, LocalDate.of(2026, 1, 20))
        assertEquals(LocalDate.of(2025, 12, 1), window.previous.start)
        assertEquals(LocalDate.of(2026, 1, 1), window.previous.endExclusive)
    }

    @Test
    fun `year window spans calendar year`() {
        val window = statsWindow(StatsPeriod.YEAR, 0, today)
        assertEquals(LocalDate.of(2026, 1, 1), window.start)
        assertEquals(LocalDate.of(2027, 1, 1), window.endExclusive)
        assertEquals(LocalDate.of(2025, 1, 1), window.previous.start)
    }

    @Test
    fun `offset shifts the whole period`() {
        val window = statsWindow(StatsPeriod.WEEK, -1, today)
        assertEquals(LocalDate.of(2026, 11, 2), window.start)
        assertEquals(LocalDate.of(2026, 11, 8), window.lastDate)
    }

    @Test
    fun `week label stays compact across year boundary`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2025, 12, 29), window.start)
        assertEquals("2025/12/29 — 2026/1/4", window.label)
    }

    @Test
    fun `week aggregation fills gaps and counts streak`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, today)
        val counts = listOf(
            DailyCountProjection("2026-11-09", 2),
            DailyCountProjection("2026-11-10", 1),
            DailyCountProjection("2026-11-12", 3),
            DailyCountProjection("2026-11-06", 1)
        )

        val stats = StatsAggregator.build(window, today, counts, emptyList())

        assertEquals(6, stats.total)
        assertEquals(1, stats.previousTotal)
        assertEquals(5, stats.delta)
        assertEquals(2, stats.longestStreak)
        assertEquals(3, stats.peakValue)
        assertEquals(LocalDate.of(2026, 11, 12), stats.peakDate)
        assertEquals(7, stats.bars.size)
        assertEquals(listOf(2, 1, 0, 3, 0, 0, 0), stats.bars.map { it.value })
        assertTrue(stats.bars[0].highlighted)
        assertTrue(stats.hasData)
    }

    @Test
    fun `month bars label every fifth day`() {
        val window = statsWindow(StatsPeriod.MONTH, 0, LocalDate.of(2026, 11, 9))
        val stats = StatsAggregator.build(window, LocalDate.of(2026, 11, 9), emptyList(), emptyList())

        assertEquals(30, stats.bars.size)
        assertEquals(listOf(1, 5, 10, 15, 20, 25, 30), stats.bars.filter { it.showLabel }.map { it.label.toInt() })
        assertFalse(stats.hasData)
    }

    @Test
    fun `year bars aggregate by month`() {
        val window = statsWindow(StatsPeriod.YEAR, 0, today)
        val counts = listOf(
            DailyCountProjection("2026-01-02", 2),
            DailyCountProjection("2026-01-03", 1),
            DailyCountProjection("2026-03-05", 4)
        )

        val stats = StatsAggregator.build(window, today, counts, emptyList())

        assertEquals(12, stats.bars.size)
        assertEquals(3, stats.bars[0].value)
        assertEquals(0, stats.bars[1].value)
        assertEquals(4, stats.bars[2].value)
        assertTrue(stats.bars[10].highlighted)
    }

    @Test
    fun `common band respects the record time zone`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, today)
        val morning = EventTimestamp(
            occurredAtEpochMillis = Instant.parse("2026-11-08T22:30:00Z").toEpochMilli(),
            zoneId = "Asia/Tokyo"
        )
        val evening = EventTimestamp(
            occurredAtEpochMillis = Instant.parse("2026-11-09T12:30:00Z").toEpochMilli(),
            zoneId = "Asia/Tokyo"
        )

        val stats = StatsAggregator.build(
            window = window,
            today = today,
            dailyCounts = emptyList(),
            timestamps = listOf(morning, evening),
            zoneFallback = ZoneId.of("Asia/Tokyo")
        )

        assertEquals("07:00–09:00", stats.commonBand)
    }

    @Test
    fun `empty period has no band and no peak`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, today)
        val stats = StatsAggregator.build(window, today, emptyList(), emptyList())

        assertEquals(0, stats.total)
        assertEquals(0, stats.longestStreak)
        assertNull(stats.peakDate)
        assertNull(stats.commonBand)
        assertEquals(0.0, stats.dailyAverage, 0.001)
        assertFalse(stats.hasData)
    }

    @Test
    fun `hourly counts are consistent with the common band`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, today)
        val zone = ZoneId.of("Asia/Tokyo")
        val stamps = listOf(
            EventTimestamp(Instant.parse("2026-11-08T22:30:00Z").toEpochMilli(), zone.id),
            EventTimestamp(Instant.parse("2026-11-08T23:30:00Z").toEpochMilli(), zone.id),
            EventTimestamp(Instant.parse("2026-11-09T12:30:00Z").toEpochMilli(), zone.id)
        )

        val stats = StatsAggregator.build(
            window = window,
            today = today,
            dailyCounts = emptyList(),
            timestamps = stamps,
            zoneFallback = zone
        )

        assertEquals(24, stats.hourlyCounts.size)
        assertEquals(3, stats.hourlyCounts.sum())
        assertEquals(1, stats.hourlyCounts[7])
        assertEquals(1, stats.hourlyCounts[8])
        assertEquals(1, stats.hourlyCounts[21])
        assertEquals(7, stats.commonBandStartHour)
        assertEquals("07:00–09:00", stats.commonBand)
        val start = stats.commonBandStartHour!!
        val best = stats.hourlyCounts[start] + stats.hourlyCounts[start + 1]
        assertTrue((0..22).all { stats.hourlyCounts[it] + stats.hourlyCounts[it + 1] <= best })
    }

    @Test
    fun `detail distribution is scoped to the current window`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, today)
        val counts = listOf(DailyCountProjection("2026-11-09", 1))
        val details = listOf(
            StatDetailProjection("2026-11-09", 4, 2, 1, 1, 0, 0),
            StatDetailProjection("2026-11-02", 5, 1, 2, 0, 1, 1)
        )

        val stats = StatsAggregator.build(
            window = window,
            today = today,
            dailyCounts = counts,
            timestamps = emptyList(),
            details = details
        )

        assertEquals(1, stats.total)
        assertEquals(1, stats.distribution.totalRecords)
        assertEquals(1, stats.distribution.filledBristol)
        assertEquals(1, stats.distribution.bristolCounts.sum())
        assertEquals(1, stats.distribution.bristolCounts[3])
        assertEquals(1, stats.distribution.amountCounts[1])
    }

    @Test
    fun `quick records without details still expose a zero filled distribution`() {
        val window = statsWindow(StatsPeriod.WEEK, 0, today)
        val counts = listOf(DailyCountProjection("2026-11-09", 3))

        val stats = StatsAggregator.build(window, today, counts, emptyList())

        assertEquals(3, stats.total)
        assertEquals(3, stats.distribution.totalRecords)
        assertEquals(0, stats.distribution.filledBristol)
        assertFalse(stats.distribution.anyFilled)
    }
}
