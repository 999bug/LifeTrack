package com.example.lifetrack.feature.sport

import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 运动子应用的派生口径：只重排已有记录，不补算热量 / 配速 / 距离。
 * 固定 today = 2026-11-13（周五），本周一为 2026-11-09。
 */
class SportStatsTest {

    private val today = LocalDate.of(2026, 11, 13)

    private fun record(
        id: String,
        date: String,
        sport: String?,
        minutes: Int?,
        intensity: String = "medium",
        note: String = "",
        typeCode: String = SportFields.TYPE_CODE
    ): BackupRecord {
        val millis = runCatching { Instant.parse("${date}T03:00:00Z").toEpochMilli() }.getOrDefault(0L)
        val detail = sport?.let {
            RecordDetail.Generic(
                SportFields.TYPE_CODE,
                buildMap {
                    put(SportFields.SPORT, it)
                    minutes?.let { value -> put(SportFields.DURATION, value.toString()) }
                    put(SportFields.INTENSITY, intensity)
                }
            )
        }
        return BackupRecord(
            event = TrackEventEntity(
                id = id,
                typeCode = typeCode,
                occurredAtEpochMillis = millis,
                localDate = date,
                zoneId = "Asia/Shanghai",
                note = note,
                createdAtEpochMillis = millis,
                updatedAtEpochMillis = millis
            ),
            detail = detail
        )
    }

    private val weekRecords = listOf(
        record("a", "2026-11-13", "yoga", 30, "low"),
        record("b", "2026-11-11", "ride", 60, "high"),
        record("c", "2026-11-09", "run", 45),
        record("d", "2026-11-08", "yoga", 20)
    )

    @Test
    fun `sessions parse fields and sort newest first`() {
        val sessions = SportStats.sessions(weekRecords)

        assertEquals(4, sessions.size)
        assertEquals(listOf("a", "b", "c", "d"), sessions.map { it.id })
        assertEquals("yoga", sessions.first().sport)
        assertEquals(30, sessions.first().minutes)
        assertEquals("low", sessions.first().intensity)
    }

    @Test
    fun `sessions skip other types bad dates and missing details`() {
        val records = listOf(
            record("x", "2026-11-13", "yoga", 30, typeCode = "water"),
            record("y", "not-a-date", "yoga", 30),
            record("z", "2026-11-12", null, null)
        )

        val sessions = SportStats.sessions(records)

        // 缺详情的运动记录仍算一次（只有「其他类型 / 日期非法」才被排除）
        assertEquals(listOf("z"), sessions.map { it.id })
        assertEquals(0, sessions.single().minutes)
    }

    @Test
    fun `week starts on monday and only counts this week`() {
        val week = SportStats.week(SportStats.sessions(weekRecords), today)

        assertEquals(LocalDate.of(2026, 11, 9), week.start)
        assertEquals(LocalDate.of(2026, 11, 15), week.end)
        assertEquals(3, week.count)
        assertEquals(135, week.totalMinutes)
        assertEquals(listOf("yoga" to 30, "ride" to 60, "run" to 45), week.knownMinutes)
        assertEquals(0, week.legacyMinutes)
    }

    @Test
    fun `week days keep seven columns with today and future flags`() {
        val week = SportStats.week(SportStats.sessions(weekRecords), today)

        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), week.days.map { it.label })
        assertEquals(7, week.days.size)
        assertEquals(30, week.days[4].minutes)
        assertTrue(week.days[4].isToday)
        assertEquals(1, week.days[4].count)
        assertTrue(week.days[6].isFuture)
        assertEquals(0, week.days[6].minutes)
    }

    /** 旧类型不进入三色占比，但必须计入次数与总时长，否则合计会对不上列表。 */
    @Test
    fun `legacy types count in totals but stay out of the three segments`() {
        val records = weekRecords + record("legacy", "2026-11-10", "walk", 20)
        val week = SportStats.week(SportStats.sessions(records), today)

        assertEquals(4, week.count)
        assertEquals(155, week.totalMinutes)
        assertEquals(listOf("yoga" to 30, "ride" to 60, "run" to 45), week.knownMinutes)
        assertEquals(20, week.legacyMinutes)
        assertEquals(listOf("yoga", "ride", "run", "walk"), week.bySport.keys.toList())
    }

    @Test
    fun `idle week reports zero and keeps a seven day skeleton`() {
        val week = SportStats.week(emptyList(), today)

        assertTrue(week.isIdle)
        assertEquals(0, week.totalMinutes)
        assertEquals(7, week.days.size)
        assertTrue(week.knownMinutes.isEmpty())
    }

    @Test
    fun `lastOf returns the most recent session of a type`() {
        val sessions = SportStats.sessions(weekRecords)

        assertEquals("a", SportStats.lastOf(sessions, "yoga")?.id)
        assertEquals("b", SportStats.lastOf(sessions, "ride")?.id)
        assertNull(SportStats.lastOf(sessions, "swim"))
    }

    @Test
    fun `brief derives average and most frequent type over four weeks`() {
        val brief = SportStats.brief(SportStats.sessions(weekRecords), today)

        assertEquals(3, brief.weekCount)
        assertEquals(135, brief.weekMinutes)
        assertEquals(45, brief.avgMinutes)
        assertEquals(4, brief.monthCount)
        assertEquals("yoga", brief.topSport)
        assertEquals(2, brief.topSportCount)
    }

    @Test
    fun `brief on empty data leaves gaps instead of fake numbers`() {
        val brief = SportStats.brief(emptyList(), today)

        assertEquals(0, brief.weekCount)
        assertEquals(0, brief.avgMinutes)
        assertEquals(0, brief.monthCount)
        assertNull(brief.topSport)
    }

    @Test
    fun `gap label describes days since last workout`() {
        assertEquals("还没有记录", SportStats.gapLabel(null, today))
        assertEquals("今天动过", SportStats.gapLabel(today, today))
        assertEquals("昨天动过", SportStats.gapLabel(today.minusDays(1), today))
        assertEquals("距上次 5 天", SportStats.gapLabel(today.minusDays(5), today))
    }
}
