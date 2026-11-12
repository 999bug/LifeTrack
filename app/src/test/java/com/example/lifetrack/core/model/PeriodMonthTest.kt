package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A9：经期专用月历可翻未来月，与历史月历各自独立（设计 §5.4 / W14）。 */
class PeriodMonthTest {

    /** 2026-11-01 是周日，周一首日需要 6 个补白。 */
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun `november grid starts on monday with leading and trailing blanks`() {
        val month = PeriodMonth.of(today, 0)

        assertEquals(LocalDate.of(2026, 11, 1), month.start)
        assertEquals(LocalDate.of(2026, 11, 30), month.last)
        assertEquals("2026年11月", month.label)
        assertEquals(42, month.cells.size)
        repeat(6) { assertNull(month.cells[it]) }
        assertEquals(LocalDate.of(2026, 11, 1), month.cells[6])
        assertEquals(LocalDate.of(2026, 11, 30), month.cells[35])
        repeat(6) { assertNull(month.cells[36 + it]) }
        assertEquals(30, month.cells.count { it != null })
    }

    @Test
    fun `future offset is allowed unlike the history calendar`() {
        val next = PeriodMonth.of(today, 1)
        assertEquals(2026, next.year)
        assertEquals(12, next.month)
        assertEquals(LocalDate.of(2026, 12, 1), next.start)

        val far = PeriodMonth.of(today, 3)
        assertEquals(2027, far.year)
        assertEquals(2, far.month)
        assertEquals(LocalDate.of(2027, 2, 1), far.start)
    }

    @Test
    fun `positive offset crosses year boundary`() {
        val month = PeriodMonth.of(LocalDate.of(2026, 12, 15), 1)

        assertEquals(2027, month.year)
        assertEquals(1, month.month)
        assertEquals(LocalDate.of(2027, 1, 1), month.start)
        assertEquals(LocalDate.of(2027, 1, 31), month.last)
    }

    @Test
    fun `negative offset crosses year boundary`() {
        val month = PeriodMonth.of(LocalDate.of(2026, 1, 20), -1)

        assertEquals(2025, month.year)
        assertEquals(12, month.month)
        assertEquals(LocalDate.of(2025, 12, 1), month.start)
        assertEquals(LocalDate.of(2025, 12, 31), month.last)
    }

    @Test
    fun `leap february renders 29 days`() {
        val month = PeriodMonth.of(LocalDate.of(2024, 2, 10), 0)

        assertEquals(LocalDate.of(2024, 2, 1), month.start)
        assertEquals(LocalDate.of(2024, 2, 29), month.last)
        assertEquals(29, month.cells.count { it != null })
    }

    @Test
    fun `offset is absolute not cumulative`() {
        assertEquals(12, PeriodMonth.of(today, 1).month)
        assertEquals(12, PeriodMonth.of(today, 1).month)
        assertEquals(2, PeriodMonth.of(today, 3).month)
        assertEquals(10, PeriodMonth.of(today, -1).month)
    }

    @Test
    fun `leading blanks always match monday first weekday`() {
        listOf(-2, -1, 0, 1, 2, 3).forEach { offset ->
            val month = PeriodMonth.of(today, offset)
            val expectedLeading = (month.start.dayOfWeek.value - 1 + 7) % 7
            assertEquals(expectedLeading, month.cells.indexOfFirst { it != null })
            assertEquals(month.start, month.cells[expectedLeading])
        }
    }
}