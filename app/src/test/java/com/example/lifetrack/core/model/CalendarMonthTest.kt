package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class CalendarMonthTest {

    /** 2026-11-09 是周一，11-01 是周日。 */
    private val today = LocalDate.of(2026, 11, 9)

    @Test
    fun `november grid starts on monday with leading and trailing blanks`() {
        val month = CalendarMonth.of(today, 0)

        assertEquals(LocalDate.of(2026, 11, 1), month.start)
        assertEquals(LocalDate.of(2026, 11, 30), month.last)
        assertEquals("2026年11月", month.label)
        assertEquals(42, month.cells.size)
        repeat(6) { assertNull(month.cells[it].date) }
        assertEquals(LocalDate.of(2026, 11, 1), month.cells[6].date)
        assertEquals(LocalDate.of(2026, 11, 30), month.cells[35].date)
        repeat(6) { assertNull(month.cells[36 + it].date) }
    }

    @Test
    fun `counts only apply to real dates`() {
        val month = CalendarMonth.of(today, 0).withCounts(
            mapOf(
                "2026-11-01" to 2,
                "2026-11-09" to 3,
                "2026-10-26" to 9
            )
        )

        assertEquals(0, month.cells[0].count)
        assertEquals(2, month.cells[6].count)
        assertEquals(3, month.cells[14].count)
        assertEquals(0, month.cells[36].count)
    }

    @Test
    fun `leap february renders 29 days`() {
        val month = CalendarMonth.of(LocalDate.of(2024, 2, 10), 0)

        assertEquals(LocalDate.of(2024, 2, 1), month.start)
        assertEquals(LocalDate.of(2024, 2, 29), month.last)
        assertEquals(29, month.cells.count { it.date != null })
    }

    @Test
    fun `negative offset crosses year boundary`() {
        val month = CalendarMonth.of(LocalDate.of(2026, 1, 20), -1)

        assertEquals(2025, month.year)
        assertEquals(12, month.month)
        assertEquals(LocalDate.of(2025, 12, 1), month.start)
        assertEquals(LocalDate.of(2025, 12, 31), month.last)
    }

    @Test
    fun `offset is absolute not cumulative`() {
        assertEquals(10, CalendarMonth.of(today, -1).month)
        assertEquals(10, CalendarMonth.of(today, -1).month)
        assertEquals(9, CalendarMonth.of(today, -2).month)
    }

    @Test
    fun `future offset is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { CalendarMonth.of(today, 1) }
    }
}
