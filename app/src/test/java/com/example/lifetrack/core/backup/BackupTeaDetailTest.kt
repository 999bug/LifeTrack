package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.TeaFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** v0.7：茶作为独立类型注册规格后，生产默认解码放行且逐字段校验。 */
class BackupTeaDetailTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-19T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-19T06:00:00Z")

    private val validFields = mapOf(
        TeaFields.VARIETY to "茉莉花茶",
        TeaFields.CUPS to "2",
        TeaFields.AMOUNT to "600",
        TeaFields.CALORIES to "10"
    )

    @Test
    fun `production defaults round trip tea detail`() {
        val result = BackupCodec.decode(encode(validFields), fixedNow)
        assertTrue(result is BackupDecodeResult.Success)
        val record = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals(TeaFields.TYPE_CODE, record.event.typeCode)
        assertEquals(RecordDetail.Generic(TeaFields.TYPE_CODE, validFields), record.detail)
    }

    @Test
    fun `rejects tea detail with out of range cups`() {
        val result = BackupCodec.decode(encode(validFields + (TeaFields.CUPS to "0")), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects unknown tea field`() {
        val result = BackupCodec.decode(encode(validFields + ("caffeine" to "low")), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `a tea record without detail is still valid`() {
        val text = BackupCodec.encode(
            DEFAULT_TYPES,
            listOf(BackupRecord(event = event(), detail = null)),
            fixedNow
        )
        val result = BackupCodec.decode(text, fixedNow)
        assertTrue(result is BackupDecodeResult.Success)
    }

    private fun encode(fields: Map<String, String>): String = BackupCodec.encode(
        types = DEFAULT_TYPES,
        events = listOf(BackupRecord(event = event(), detail = RecordDetail.Generic(TeaFields.TYPE_CODE, fields))),
        now = fixedNow
    )

    private fun event() = TrackEventEntity(
        id = "t1",
        typeCode = TeaFields.TYPE_CODE,
        occurredAtEpochMillis = occurredMillis,
        localDate = occurredDate,
        zoneId = tokyo.id,
        note = "",
        createdAtEpochMillis = occurredMillis,
        updatedAtEpochMillis = occurredMillis
    )
}
