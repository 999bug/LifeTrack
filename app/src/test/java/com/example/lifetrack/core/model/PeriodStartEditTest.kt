package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 「设为开始日」纯计划的边界覆盖。
 *
 * 场景来自真实反馈：整段全是自动延续标出来的日子（11-05 起没点「走了」，一路记到今天），
 * 事后想把开始日挪到 11-09，界面却怎么都改不动。
 */
class PeriodStartEditTest {

    private fun dates(startIso: String, days: Int): List<LocalDate> {
        val start = LocalDate.parse(startIso)
        return (0 until days).map { start.plusDays(it.toLong()) }
    }

    @Test
    fun `moving start later drops the days in between`() {
        val plan = PeriodStartEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 5),
            newStart = LocalDate.of(2026, 11, 9),
            lockedDates = emptySet()
        )

        assertTrue(plan.canApply)
        assertEquals(dates("2026-11-05", 4), plan.dropDates)
        assertTrue(plan.blockedDates.isEmpty())
    }

    @Test
    fun `moving start by one day drops exactly one day`() {
        val plan = PeriodStartEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 5),
            newStart = LocalDate.of(2026, 11, 6),
            lockedDates = emptySet()
        )

        assertEquals(listOf(LocalDate.of(2026, 11, 5)), plan.dropDates)
    }

    @Test
    fun `any filled day inside the range blocks the whole change`() {
        val plan = PeriodStartEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 5),
            newStart = LocalDate.of(2026, 11, 9),
            lockedDates = setOf(LocalDate.of(2026, 11, 6))
        )

        assertFalse(plan.canApply)
        assertEquals(listOf(LocalDate.of(2026, 11, 6)), plan.blockedDates)
        // 能删的那几天也不动：只删一半会留下 5 日、7 日、8 日这种孤立段。
        assertEquals(
            listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)),
            plan.dropDates
        )
    }

    @Test
    fun `filled days after the new start do not block`() {
        val plan = PeriodStartEdit.plan(
            segmentStart = LocalDate.of(2026, 11, 5),
            newStart = LocalDate.of(2026, 11, 9),
            lockedDates = setOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11))
        )

        assertTrue(plan.canApply)
        assertEquals(dates("2026-11-05", 4), plan.dropDates)
    }

    @Test
    fun `same or earlier start is a no-op`() {
        listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)).forEach { newStart ->
            val plan = PeriodStartEdit.plan(
                segmentStart = LocalDate.of(2026, 11, 5),
                newStart = newStart,
                lockedDates = emptySet()
            )

            assertTrue(plan.canApply)
            assertTrue(plan.dropDates.isEmpty())
        }
    }

    @Test
    fun `range crosses month boundary correctly`() {
        val plan = PeriodStartEdit.plan(
            segmentStart = LocalDate.of(2026, 10, 30),
            newStart = LocalDate.of(2026, 11, 2),
            lockedDates = emptySet()
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 11, 1)
            ),
            plan.dropDates
        )
    }
}
