package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 排便用时口径：自动计时、阈值、手填优先与展示文案。 */
class PoopFieldsTest {

    private val start = 1_762_656_000_000L

    @Test
    fun `measured duration rounds up to whole minutes`() {
        assertEquals(1, PoopFields.measuredMinutes(start, start + 1_000))
        assertEquals(1, PoopFields.measuredMinutes(start, start + 60_000))
        assertEquals(2, PoopFields.measuredMinutes(start, start + 60_001))
        assertEquals(12, PoopFields.measuredMinutes(start, start + 12 * 60_000L))
        // 结束早于开始（改过开始时间）不算时长，交给阈值判定为无效。
        assertEquals(0, PoopFields.measuredMinutes(start, start - 1))
    }

    @Test
    fun `measured duration is valid only up to one hour`() {
        assertTrue(PoopFields.isMeasuredValid(1))
        assertTrue(PoopFields.isMeasuredValid(PoopFields.MAX_DURATION_MINUTES))
        assertFalse(PoopFields.isMeasuredValid(0))
        assertFalse(PoopFields.isMeasuredValid(PoopFields.MAX_DURATION_MINUTES + 1))
    }

    @Test
    fun `ending within the window stores both end time and duration`() {
        val end = start + 7 * 60_000L
        val timing = PoopFields.resolve(start, end, "")
        assertEquals(7, timing.durationMinutes)
        assertEquals(end, timing.endedAtEpochMillis)
    }

    @Test
    fun `ending after the threshold stores nothing`() {
        val timing = PoopFields.resolve(start, start + 61 * 60_000L, "")
        assertNull(timing.durationMinutes)
        assertNull(timing.endedAtEpochMillis)
    }

    @Test
    fun `manual duration wins and clears the end time`() {
        val end = start + 5 * 60_000L
        val timing = PoopFields.resolve(start, end, "25")
        assertEquals(25, timing.durationMinutes)
        assertNull(timing.endedAtEpochMillis)
    }

    @Test
    fun `invalid manual duration never reaches storage`() {
        listOf("0", "61", "abc").forEach { value ->
            val timing = PoopFields.resolve(start, start + 5 * 60_000L, value)
            assertNull(value, timing.durationMinutes)
            assertNull(value, timing.endedAtEpochMillis)
        }
    }

    @Test
    fun `no end time and no manual input stays ongoing`() {
        val timing = PoopFields.resolve(start, null, "")
        assertNull(timing.durationMinutes)
        assertNull(timing.endedAtEpochMillis)
    }

    @Test
    fun `labels cover recorded and missing duration`() {
        assertEquals("12 分钟", PoopFields.durationLabel(12))
        assertEquals("60 分钟", PoopFields.durationLabel(60))
        assertEquals("未记录用时", PoopFields.durationLabel(null))
        assertEquals(1..60, PoopFields.DURATION_RANGE)
    }

    @Test
    fun `manual duration accepts only one to sixty`() {
        assertTrue(PoopFields.isValidManualDuration("1"))
        assertTrue(PoopFields.isValidManualDuration("60"))
        listOf("0", "61", "", "-3", "12.5").forEach {
            assertFalse(it, PoopFields.isValidManualDuration(it))
        }
    }
}
