package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** A8：运动详情注册规格后，生产默认解码必须放行且仍逐字段校验。 */
class BackupSportDetailTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-09T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-09T06:00:00Z")

    private val validFields = mapOf(
        SportFields.SPORT to "ride",
        SportFields.DURATION to "80",
        SportFields.INTENSITY to "medium"
    )

    @Test
    fun `production defaults round trip sport detail`() {
        val result = BackupCodec.decode(encode(validFields), fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val record = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals(SportFields.TYPE_CODE, record.event.typeCode)
        assertEquals(RecordDetail.Generic(SportFields.TYPE_CODE, validFields), record.detail)
    }

    @Test
    fun `rejects sport duration out of range`() {
        listOf("0", "1441", "abc", "").forEach { value ->
            val fields = validFields + (SportFields.DURATION to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(value, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `rejects an unknown sport field`() {
        val fields = validFields + ("distanceKm" to "12")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects illegal sport or intensity values`() {
        listOf(
            SportFields.SPORT to "ski",
            SportFields.INTENSITY to "extreme"
        ).forEach { (key, value) ->
            val fields = validFields + (key to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(key, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `a sport record without detail is still valid`() {
        val record = BackupRecord(event = event(), detail = null)
        val text = BackupCodec.encode(DEFAULT_TYPES, listOf(record), fixedNow)
        val result = BackupCodec.decode(text, fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        assertEquals(null, (result as BackupDecodeResult.Success).payload.events.single().detail)
    }

    private fun encode(fields: Map<String, String>): String = BackupCodec.encode(
        types = DEFAULT_TYPES,
        events = listOf(
            BackupRecord(
                event = event(),
                detail = RecordDetail.Generic(SportFields.TYPE_CODE, fields)
            )
        ),
        now = fixedNow
    )

    private fun event() = TrackEventEntity(
        id = "s1",
        typeCode = SportFields.TYPE_CODE,
        occurredAtEpochMillis = occurredMillis,
        localDate = occurredDate,
        zoneId = tokyo.id,
        note = "",
        createdAtEpochMillis = occurredMillis,
        updatedAtEpochMillis = occurredMillis
    )
}
