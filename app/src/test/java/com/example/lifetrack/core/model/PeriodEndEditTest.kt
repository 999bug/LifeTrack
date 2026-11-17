package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 「改结束日期」纯计划的边界覆盖：向后补写 / 向前撤销 / 同日无操作 / 已记录日不重复补。
 *
 * 场景来自真实反馈——忘了点「大姨妈走了」，11-09 起自动延续到 11-17，
 * 事后想把结束日改回 11-13。
 */
class PeriodEndEditTest {

    private val today = LocalDate.of(2026, 11, 17)

    private fun period(startIso: String, days: Int): Set<LocalDate> {
        val start = LocalDate.parse(startIso)
        return (0 until days).map { start.plusDays(it.toLong()) }.toSet()
    }

    @Test
    fun `shortening trims the days after the new end`() {
        val recorded = period("2026-11-09", 9) // 11-09 .. 11-17
        val plan = PeriodEndEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 9),
            segmentEnd = LocalDate.of(2026, 11, 17),
            newEnd = LocalDate.of(2026, 11, 13),
            recordedDates = recorded
        )

        assertTrue(plan.fillDates.isEmpty())
        assertEquals(period("2026-11-14", 4).toList().sorted(), plan.trimDates)
    }

    @Test
    fun `extending fills only the missing days`() {
        val recorded = period("2026-11-09", 3) // 11-09 .. 11-11
        val plan = PeriodEndEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 9),
            segmentEnd = LocalDate.of(2026, 11, 11),
            newEnd = LocalDate.of(2026, 11, 14),
            recordedDates = recorded
        )

        assertEquals(
            listOf(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 14)),
            plan.fillDates
        )
        assertTrue(plan.trimDates.isEmpty())
    }

    @Test
    fun `same end date is a no-op`() {
        val end = LocalDate.of(2026, 11, 13)
        val plan = PeriodEndEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 9),
            segmentEnd = end,
            newEnd = end,
            recordedDates = period("2026-11-09", 5)
        )

        assertTrue(plan.fillDates.isEmpty())
        assertTrue(plan.trimDates.isEmpty())
    }

    @Test
    fun `gaps inside the segment are filled without touching recorded days`() {
        val recorded = setOf(
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 13) // 用户手填过的一天，补齐时不能覆盖
        )
        val plan = PeriodEndEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 9),
            segmentEnd = LocalDate.of(2026, 11, 13),
            newEnd = LocalDate.of(2026, 11, 13),
            recordedDates = recorded
        )

        assertEquals(
            listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12)),
            plan.fillDates
        )
        assertTrue(plan.trimDates.isEmpty())
    }

    @Test
    fun `end before segment start is clamped to the start day`() {
        val start = LocalDate.of(2026, 11, 9)
        val plan = PeriodEndEdit.plan(
            segmentStart = start,
            segmentEnd = LocalDate.of(2026, 11, 17),
            newEnd = LocalDate.of(2026, 11, 1),
            recordedDates = period("2026-11-09", 9)
        )

        assertTrue(plan.fillDates.isEmpty())
        assertEquals(period("2026-11-10", 8).toList().sorted(), plan.trimDates)
    }

    @Test
    fun `end equal to today trims nothing`() {
        val plan = PeriodEndEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 14),
            segmentEnd = today,
            newEnd = today,
            recordedDates = period("2026-11-14", 4)
        )

        assertTrue(plan.fillDates.isEmpty())
        assertTrue(plan.trimDates.isEmpty())
    }
}
