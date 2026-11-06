package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecordTimeMapperTest {
    @Test
    fun `maps UTC instant to Tokyo local date`() {
        val instant = Instant.parse("2026-11-05T16:30:00Z").toEpochMilli()
        val metadata = RecordTimeMapper.from(instant, "Asia/Tokyo")
        assertEquals("2026-11-05T16:30:00Z", Instant.ofEpochMilli(metadata.occurredAtEpochMillis).toString())
        assertEquals("2026-11-06", metadata.localDate)
        assertEquals("Asia/Tokyo", metadata.zoneId)
    }

    @Test
    fun `same instant can belong to previous date in Los Angeles`() {
        val instant = Instant.parse("2026-11-06T00:30:00Z").toEpochMilli()
        val metadata = RecordTimeMapper.from(instant, "America/Los_Angeles")
        assertEquals("2026-11-05", metadata.localDate)
    }

    @Test
    fun `invalid time zone is rejected`() {
        val instant = Instant.parse("2026-11-06T00:30:00Z").toEpochMilli()
        try {
            RecordTimeMapper.from(instant, "Mars/Olympus")
            throw AssertionError("Expected DateTimeException")
        } catch (_: java.time.DateTimeException) {
            // Expected: only valid IANA zone IDs are accepted.
        }
    }
}
