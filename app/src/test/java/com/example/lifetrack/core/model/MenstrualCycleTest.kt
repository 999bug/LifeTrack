package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A9：周期推算纯函数的边界覆盖（设计 §5.1 单测清单）。全部用 LocalDate，与时区 / 夏令时无关。 */
class MenstrualCycleTest {

    private val today = LocalDate.of(2026, 11, 12)

    private fun dates(vararg iso: String): Set<LocalDate> = iso.map(LocalDate::parse).toSet()

    private fun period(startIso: String, days: Int): Set<LocalDate> {
        val start = LocalDate.parse(startIso)
        return (0 until days).map { start.plusDays(it.toLong()) }.toSet()
    }

    private fun calc(
        recorded: Set<LocalDate>,
        today: LocalDate = this.today,
        cycle: Int = 28,
        period: Int = 5,
        sampleSize: Int = 6
    ) = CycleCalculator.calculate(recorded, today, cycle, period, sampleSize)

    @Test
    fun `empty records give insufficient phase and no prediction`() {
        val snapshot = calc(emptySet())

        assertTrue(snapshot.segments.isEmpty())
        assertTrue(snapshot.cycleLengths.isEmpty())
        assertNull(snapshot.medianCycleDays)
        assertNull(snapshot.medianPeriodDays)
        assertEquals(Phase.Insufficient, snapshot.currentPhase)
        assertTrue(snapshot.predictedPeriods.isEmpty())
        assertFalse(snapshot.sampleReliable)
    }

    @Test
    fun `single recorded day is a one day segment`() {
        val snapshot = calc(dates("2026-11-12"))

        assertEquals(1, snapshot.segments.size)
        assertEquals(CycleSegment(today, today, 1), snapshot.segments.single())
        assertEquals(1, snapshot.medianPeriodDays)
        assertNull(snapshot.medianCycleDays)
        assertEquals(Phase.InPeriod(1), snapshot.currentPhase)
        assertFalse(snapshot.sampleReliable)
    }

    @Test
    fun `five consecutive days ending today merge into one segment`() {
        val snapshot = calc(period("2026-11-08", 5))

        assertEquals(
            CycleSegment(LocalDate.of(2026, 11, 8), today, 5),
            snapshot.segments.single()
        )
        assertEquals(5, snapshot.medianPeriodDays)
        assertEquals(Phase.InPeriod(5), snapshot.currentPhase)
        assertTrue(snapshot.predictedPeriods.isEmpty())
    }

    @Test
    fun `a missing middle day splits the segment`() {
        val snapshot = calc(dates("2026-11-01", "2026-11-02", "2026-11-04", "2026-11-05"))

        assertEquals(2, snapshot.segments.size)
        assertEquals(listOf(2, 2), snapshot.segments.map { it.lengthDays })
        assertEquals(listOf(3), snapshot.cycleLengths)
        assertFalse(snapshot.sampleReliable)
    }

    @Test
    fun `consecutive days across year boundary merge into one segment`() {
        val snapshot = calc(
            dates("2025-12-30", "2025-12-31", "2026-01-01", "2026-01-02"),
            today = LocalDate.of(2026, 1, 2)
        )

        assertEquals(
            CycleSegment(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2), 4),
            snapshot.segments.single()
        )
        assertEquals(Phase.InPeriod(4), snapshot.currentPhase)
    }

    @Test
    fun `future dates are ignored`() {
        val snapshot = calc(
            dates("2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13", "2026-11-20")
        )

        assertEquals(
            CycleSegment(LocalDate.of(2026, 11, 10), today, 3),
            snapshot.segments.single()
        )
        assertEquals(Phase.InPeriod(3), snapshot.currentPhase)
    }

    @Test
    fun `abnormal cycle length is kept for display but excluded from median`() {
        val recorded = dates("2026-01-01", "2026-01-06", "2026-02-03", "2026-03-03", "2026-03-31")
        val snapshot = calc(recorded, today = LocalDate.of(2026, 3, 31))

        assertEquals(listOf(5, 28, 28, 28), snapshot.cycleLengths)
        assertEquals(28, snapshot.medianCycleDays)
        assertTrue(snapshot.sampleReliable)
    }

    @Test
    fun `even number of samples takes floor of the middle two`() {
        val twoSamples = calc(
            dates("2026-01-01", "2026-01-29", "2026-02-28"),
            today = LocalDate.of(2026, 3, 1)
        )
        assertEquals(listOf(28, 30), twoSamples.cycleLengths)
        assertEquals(29, twoSamples.medianCycleDays)
        assertFalse(twoSamples.sampleReliable)

        val fourSamples = calc(
            dates("2026-01-01", "2026-01-29", "2026-02-27", "2026-03-29", "2026-04-29"),
            today = LocalDate.of(2026, 4, 29)
        )
        assertEquals(listOf(28, 29, 30, 31), fourSamples.cycleLengths)
        assertEquals(29, fourSamples.medianCycleDays)
        assertTrue(fourSamples.sampleReliable)
    }

    @Test
    fun `insufficient valid samples fall back and do not predict`() {
        val snapshot = calc(
            dates("2026-10-01", "2026-10-29"),
            today = LocalDate.of(2026, 11, 10)
        )

        assertEquals(listOf(28), snapshot.cycleLengths)
        assertEquals(28, snapshot.medianCycleDays)
        assertFalse(snapshot.sampleReliable)
        assertTrue(snapshot.predictedPeriods.isEmpty())
        assertEquals(Phase.Insufficient, snapshot.currentPhase)
    }

    @Test
    fun `reliable samples predict three future windows from the last start`() {
        val recorded = period("2026-01-01", 5) + period("2026-01-29", 5) +
            period("2026-02-26", 5) + period("2026-03-26", 5)
        val snapshot = calc(recorded, today = LocalDate.of(2026, 3, 28))

        assertTrue(snapshot.sampleReliable)
        assertEquals(28, snapshot.medianCycleDays)
        assertEquals(5, snapshot.medianPeriodDays)
        assertEquals(3, snapshot.predictedPeriods.size)
        assertEquals(
            listOf(
                DateRange(LocalDate.of(2026, 4, 23), LocalDate.of(2026, 4, 27)),
                DateRange(LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 25)),
                DateRange(LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 22))
            ),
            snapshot.predictedPeriods
        )
        assertEquals(5, snapshot.predictedPeriods.first().lengthDays)
    }

    @Test
    fun `today inside a recorded segment reports the day index`() {
        val recorded = period("2026-01-01", 5) + period("2026-01-29", 5) +
            period("2026-02-26", 5) + period("2026-03-26", 5)
        val snapshot = calc(recorded, today = LocalDate.of(2026, 3, 28))

        assertEquals(Phase.InPeriod(3), snapshot.currentPhase)
    }

    @Test
    fun `today before the next predicted start reports days remaining`() {
        val recorded = period("2026-01-01", 5) + period("2026-01-29", 5) +
            period("2026-02-26", 5) + period("2026-03-26", 5)
        val snapshot = calc(recorded, today = LocalDate.of(2026, 4, 10))

        assertEquals(Phase.BeforeNext(13), snapshot.currentPhase)
    }

    @Test
    fun `today past the next predicted start reports delayed days`() {
        val recorded = period("2026-01-01", 5) + period("2026-01-29", 5) +
            period("2026-02-26", 5) + period("2026-03-26", 5)
        val snapshot = calc(recorded, today = LocalDate.of(2026, 4, 30))

        assertEquals(Phase.Delayed(7), snapshot.currentPhase)
    }

    @Test
    fun `period length median comes from the last segments`() {
        val recorded = period("2026-01-01", 3) + period("2026-01-29", 5) +
            period("2026-02-26", 7) + period("2026-03-26", 5)
        val snapshot = calc(recorded, today = LocalDate.of(2026, 3, 30))

        assertEquals(listOf(3, 5, 7, 5), snapshot.segments.map { it.lengthDays })
        assertEquals(5, snapshot.medianPeriodDays)
    }

    @Test
    fun `sample size limits how many recent samples feed the median`() {
        val recorded = dates("2026-01-01", "2026-01-29", "2026-02-27", "2026-03-29", "2026-04-29")
        val snapshot = calc(recorded, today = LocalDate.of(2026, 4, 29), sampleSize = 2)

        assertEquals(listOf(28, 29, 30, 31), snapshot.cycleLengths)
        assertEquals(30, snapshot.medianCycleDays)
        assertTrue(snapshot.sampleReliable)
    }

    @Test
    fun `date range contains both endpoints`() {
        val range = DateRange(LocalDate.of(2026, 4, 23), LocalDate.of(2026, 4, 27))

        assertEquals(5, range.lengthDays)
        assertTrue(LocalDate.of(2026, 4, 23) in range)
        assertTrue(LocalDate.of(2026, 4, 27) in range)
        assertFalse(LocalDate.of(2026, 4, 22) in range)
        assertFalse(LocalDate.of(2026, 4, 28) in range)
    }
}