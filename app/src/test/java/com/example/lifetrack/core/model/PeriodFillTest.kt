package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A9：「走了」补齐计划的边界覆盖（设计 §5.2）。纯 LocalDate，验证间隔上限 / 幂等 / 未来日期 / 段首日回退。 */
class PeriodFillTest {

    private val today = LocalDate.of(2026, 11, 12)

    private fun dates(vararg iso: String): Set<LocalDate> = iso.map(LocalDate::parse).toSet()

    private fun period(startIso: String, days: Int): Set<LocalDate> {
        val start = LocalDate.parse(startIso)
        return (0 until days).map { start.plusDays(it.toLong()) }.toSet()
    }

    private fun fill(recorded: Set<LocalDate>, endIso: String, today: LocalDate = this.today): List<LocalDate> {
        val plan = PeriodFill.plan(recorded, LocalDate.parse(endIso), today)
        assertTrue("expected Fill but got $plan", plan is PeriodFill.Plan.Fill)
        return (plan as PeriodFill.Plan.Fill).dates
    }

    @Test
    fun `empty records fill only the end day`() {
        assertEquals(listOf(today), fill(emptySet(), "2026-11-12"))
    }

    @Test
    fun `fills missing days from segment start and skips existing ones`() {
        val recorded = period("2026-11-08", 2) // 11-08, 11-09 已记录

        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), today),
            fill(recorded, "2026-11-12")
        )
    }

    @Test
    fun `fully recorded segment yields an empty fill and overwrites nothing`() {
        assertEquals(emptyList<LocalDate>(), fill(period("2026-11-08", 5), "2026-11-12"))
    }

    @Test
    fun `gap of exactly fourteen days still fills`() {
        val recorded = dates("2026-10-29")
        val filled = fill(recorded, "2026-11-12")

        assertEquals(14, filled.size)
        assertEquals(LocalDate.of(2026, 10, 30), filled.first())
        assertEquals(today, filled.last())
        assertTrue(LocalDate.of(2026, 10, 29) !in filled) // 已有记录不重复补
    }

    @Test
    fun `gap beyond fourteen days returns too far`() {
        val plan = PeriodFill.plan(dates("2026-10-28"), LocalDate.of(2026, 11, 12), today)

        assertEquals(PeriodFill.Plan.TooFar, plan)
    }

    @Test
    fun `long abandoned gap returns too far`() {
        val plan = PeriodFill.plan(period("2026-10-01", 2), LocalDate.of(2026, 11, 12), today)

        assertEquals(PeriodFill.Plan.TooFar, plan)
    }

    @Test
    fun `future days are never filled`() {
        val recorded = period("2026-11-08", 2)

        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), today),
            fill(recorded, "2026-11-15")
        )
    }

    @Test
    fun `end before today fills up to the end day`() {
        val recorded = period("2026-11-08", 3) // 11-08..11-10

        assertEquals(listOf(LocalDate.of(2026, 11, 11)), fill(recorded, "2026-11-11"))
    }

    @Test
    fun `end before any segment start falls back to filling just the end day`() {
        val recorded = period("2026-11-10", 3) // 段首日 11-10 晚于选中日 11-08

        assertEquals(listOf(LocalDate.of(2026, 11, 8)), fill(recorded, "2026-11-08"))
    }
}