package com.example.lifetrack.feature.history

import com.example.lifetrack.core.database.model.DailyTypeCountProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCalendarDataTest {

    private fun row(date: String, type: String, count: Int, firstAt: Long) =
        DailyTypeCountProjection(localDate = date, typeCode = type, count = count, firstAt = firstAt)

    @Test
    fun `types are grouped by date and keep first-seen order`() {
        val rows = listOf(
            row("2026-11-11", "poop", 1, 100L),
            row("2026-11-11", "water", 3, 200L),
            row("2026-11-11", "coffee", 1, 300L),
            row("2026-11-10", "water", 2, 50L)
        )

        val result = DayCalendarData.typesByDate(rows)

        assertEquals(listOf("poop", "water", "coffee"), result["2026-11-11"])
        assertEquals(listOf("water"), result["2026-11-10"])
        assertEquals(2, result.size)
    }

    @Test
    fun `same type on the same day collapses into one mark`() {
        // DAO 已经 GROUP BY，这里再挡一层：输入里出现重复类型也不能画出两个同样的图标。
        val rows = listOf(
            row("2026-11-11", "water", 1, 100L),
            row("2026-11-11", "water", 2, 200L)
        )

        val result = DayCalendarData.typesByDate(rows)

        assertEquals(listOf("water"), result["2026-11-11"])
    }

    @Test
    fun `more than three types keep all codes and the view truncates`() {
        val rows = listOf(
            row("2026-11-11", "poop", 1, 10L),
            row("2026-11-11", "water", 1, 20L),
            row("2026-11-11", "coffee", 1, 30L),
            row("2026-11-11", "sport", 1, 40L)
        )

        val result = DayCalendarData.typesByDate(rows)
        val all = result.getValue("2026-11-11")

        // 数据层不丢信息，截断只发生在界面层
        assertEquals(4, all.size)
        assertEquals(
            listOf("poop", "water", "coffee"),
            DayCalendarData.visibleMarks(all)
        )
    }

    @Test
    fun `empty input yields empty map`() {
        assertTrue(DayCalendarData.typesByDate(emptyList()).isEmpty())
    }
}
