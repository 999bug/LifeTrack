package com.example.lifetrack.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerDateMapperTest {

    @Test
    fun `round trips a normal date`() {
        val date = LocalDate.of(2026, 11, 9)
        assertEquals(date, PickerDateMapper.fromUtcMillis(PickerDateMapper.toUtcMillis(date)))
    }

    @Test
    fun `round trips a leap day`() {
        val date = LocalDate.of(2024, 2, 29)
        assertEquals(date, PickerDateMapper.fromUtcMillis(PickerDateMapper.toUtcMillis(date)))
    }

    @Test
    fun `round trips year boundaries`() {
        listOf(
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2000, 1, 1)
        ).forEach { date ->
            assertEquals(date, PickerDateMapper.fromUtcMillis(PickerDateMapper.toUtcMillis(date)))
        }
    }

    @Test
    fun `maps to utc midnight regardless of device zone`() {
        val date = LocalDate.of(2026, 11, 9)
        val instant = Instant.ofEpochMilli(PickerDateMapper.toUtcMillis(date))
        assertEquals("2026-11-09T00:00:00Z", instant.toString())
        assertEquals(ZoneOffset.UTC, instant.atZone(ZoneOffset.UTC).offset)
    }

    @Test
    fun `keeps the date when the picker returns a time within the day`() {
        val noonUtc = Instant.parse("2026-11-09T12:34:56Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 9), PickerDateMapper.fromUtcMillis(noonUtc))
    }
}
