package com.example.lifetrack.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TodaySourceTest {

    @Test
    fun refreshPublishesNewDateAfterMidnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = MutableClock(
            instant = Instant.parse("2026-11-19T15:59:59Z"),
            zone = zone
        )
        val source = TodaySource(clock)

        assertEquals(LocalDate.of(2026, 11, 19), source.today.value)

        clock.instant = Instant.parse("2026-11-19T16:00:00Z")
        source.refresh()

        assertEquals(LocalDate.of(2026, 11, 20), source.today.value)
    }

    @Test
    fun millisUntilNextDayReturnsRemainingTimeBeforeMidnight() {
        val clock = MutableClock(
            instant = Instant.parse("2026-11-19T15:59:59Z"),
            zone = ZoneId.of("Asia/Shanghai")
        )
        val source = TodaySource(clock)

        assertEquals(1_000L, source.millisUntilNextDay())
    }

    private class MutableClock(
        var instant: Instant,
        private val zone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

        override fun instant(): Instant = instant
    }
}
