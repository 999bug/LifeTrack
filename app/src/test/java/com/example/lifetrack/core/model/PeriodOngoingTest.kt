package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A9：「点一次来了就自动延续」的纯函数边界（设计 §5.2）。
 * 三条口径：延续跨度上限、区间含两端、落库只补到昨天且不碰已有记录。
 */
class PeriodOngoingTest {

    private val today = LocalDate.of(2026, 11, 12)

    private fun dates(vararg iso: String): Set<LocalDate> = iso.map(LocalDate::parse).toSet()

    @Test
    fun canAutoContinue_acceptsToday() {
        assertTrue(PeriodOngoing.canAutoContinue(today, today))
    }

    /** 14 天是上限（含），再早就是补记历史，不该延续到今天。 */
    @Test
    fun canAutoContinue_acceptsBoundaryDay() {
        assertTrue(PeriodOngoing.canAutoContinue(today.minusDays(14), today))
    }

    @Test
    fun canAutoContinue_rejectsOlderStart() {
        assertFalse(PeriodOngoing.canAutoContinue(today.minusDays(15), today))
    }

    @Test
    fun canAutoContinue_rejectsFutureStart() {
        assertFalse(PeriodOngoing.canAutoContinue(today.plusDays(1), today))
    }

    @Test
    fun span_includesBothEnds() {
        val span = PeriodOngoing.span(LocalDate.parse("2026-11-10"), today)
        assertEquals(3, span.size)
        assertEquals(LocalDate.parse("2026-11-10"), span.first())
        assertEquals(today, span.last())
    }

    @Test
    fun span_singleDayIsOneElement() {
        assertEquals(listOf(today), PeriodOngoing.span(today, today))
    }

    @Test
    fun span_returnsEmptyWhenEndBeforeStart() {
        assertTrue(PeriodOngoing.span(today, today.minusDays(1)).isEmpty())
    }

    /** 今天由派生显示，不提前入库；昨天及更早的缺失日才补。 */
    @Test
    fun pendingWrites_stopsAtYesterday() {
        val start = today.minusDays(3)
        val pending = PeriodOngoing.pendingWrites(start, emptySet(), today)
        assertEquals(3, pending.size)
        assertEquals(start, pending.first())
        assertEquals(today.minusDays(1), pending.last())
        assertFalse(today in pending)
    }

    @Test
    fun pendingWrites_skipsRecordedDays() {
        val start = today.minusDays(3)
        val recorded = dates("2026-11-11") // 昨天已有详情，不能覆盖
        val pending = PeriodOngoing.pendingWrites(start, recorded, today)
        assertEquals(dates("2026-11-09", "2026-11-10"), pending.toSet())
    }

    @Test
    fun pendingWrites_emptyWhenStartIsToday() {
        assertTrue(PeriodOngoing.pendingWrites(today, emptySet(), today).isEmpty())
    }

    /** 写库后再算一次必须为空，保证启动 / 跨天重复触发也不会重复插入。 */
    @Test
    fun pendingWrites_isIdempotentAfterWrite() {
        val start = today.minusDays(5)
        val first = PeriodOngoing.pendingWrites(start, emptySet(), today)
        val recorded = first.toSet()
        assertTrue(PeriodOngoing.pendingWrites(start, recorded, today).isEmpty())
    }

    @Test
    fun closingDates_includesEndDay() {
        val start = LocalDate.parse("2026-11-08")
        val dates = PeriodOngoing.closingDates(start, today)
        assertEquals(5, dates.size)
        assertEquals(start, dates.first())
        assertEquals(today, dates.last())
    }

    @Test
    fun closingDates_singleDay() {
        assertEquals(listOf(today), PeriodOngoing.closingDates(today, today))
    }
}
