package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.WaterFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** A6：饮水详情已落库后，生产默认解码选项必须放行 water 且仍逐字段校验。 */
class BackupWaterDetailTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-09T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-09T06:00:00Z")

    private val validFields = mapOf(WaterFields.AMOUNT to "250", WaterFields.UNIT to "ml")

    @Test
    fun `production defaults round trip water detail`() {
        val result = BackupCodec.decode(encode(validFields), fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val record = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals(WaterFields.TYPE_CODE, record.event.typeCode)
        assertEquals(RecordDetail.Generic(WaterFields.TYPE_CODE, validFields), record.detail)
    }

    @Test
    fun `rejects water amount out of range`() {
        listOf("0", "5001", "abc", "").forEach { value ->
            val fields = validFields + (WaterFields.AMOUNT to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(value, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `rejects an unknown water field`() {
        val fields = validFields + ("caffeine" to "yes")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an unregistered unit`() {
        val fields = validFields + (WaterFields.UNIT to "litre")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a detail type that disagrees with the event`() {
        val result = BackupCodec.decode(encode(validFields, detailType = "coffee"), fixedNow)
        assertEquals(BackupError.DETAIL_TYPE_MISMATCH, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `a quick water record without detail is still valid`() {
        val record = BackupRecord(event = event(), detail = null)
        val text = BackupCodec.encode(DEFAULT_TYPES, listOf(record), fixedNow)
        val result = BackupCodec.decode(text, fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        assertEquals(null, (result as BackupDecodeResult.Success).payload.events.single().detail)
    }

    private fun encode(
        fields: Map<String, String>,
        detailType: String = WaterFields.TYPE_CODE
    ): String = BackupCodec.encode(
        types = DEFAULT_TYPES,
        events = listOf(
            BackupRecord(
                event = event(),
                detail = RecordDetail.Generic(detailType, fields)
            )
        ),
        now = fixedNow
    )

    private fun event() = TrackEventEntity(
        id = "w1",
        typeCode = WaterFields.TYPE_CODE,
        occurredAtEpochMillis = occurredMillis,
        localDate = occurredDate,
        zoneId = tokyo.id,
        note = "",
        createdAtEpochMillis = occurredMillis,
        updatedAtEpochMillis = occurredMillis
    )
}
