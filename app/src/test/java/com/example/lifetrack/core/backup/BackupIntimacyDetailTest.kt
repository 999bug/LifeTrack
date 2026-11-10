package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.RecordDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** A10：亲密关系详情注册规格后，生产默认解码必须放行且仍逐字段校验。 */
class BackupIntimacyDetailTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-09T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-09T06:00:00Z")

    private val validFields = mapOf(
        IntimacyFields.PROTECTION to "yes",
        IntimacyFields.EXPERIENCE to "good",
        IntimacyFields.DURATION to "20"
    )

    @Test
    fun `production defaults round trip intimacy detail`() {
        val result = BackupCodec.decode(encode(validFields), fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val record = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals(IntimacyFields.TYPE_CODE, record.event.typeCode)
        assertEquals(RecordDetail.Generic(IntimacyFields.TYPE_CODE, validFields), record.detail)
    }

    @Test
    fun `partial fields are valid because all three are optional`() {
        listOf(
            emptyMap(),
            mapOf(IntimacyFields.PROTECTION to "na"),
            mapOf(IntimacyFields.EXPERIENCE to "bad"),
            mapOf(IntimacyFields.DURATION to "90")
        ).forEach { fields ->
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertTrue(fields.toString(), result is BackupDecodeResult.Success)
        }
    }

    @Test
    fun `rejects intimacy duration out of range`() {
        listOf("0", "1441", "abc").forEach { value ->
            val fields = validFields + (IntimacyFields.DURATION to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(value, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `rejects a partner identity field`() {
        val fields = validFields + ("partnerName" to "someone")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects illegal protection or experience values`() {
        listOf(
            IntimacyFields.PROTECTION to "maybe",
            IntimacyFields.EXPERIENCE to "great"
        ).forEach { (key, value) ->
            val fields = validFields + (key to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(key, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `an intimacy record without detail is still valid`() {
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
                detail = RecordDetail.Generic(IntimacyFields.TYPE_CODE, fields)
            )
        ),
        now = fixedNow
    )

    private fun event() = TrackEventEntity(
        id = "i1",
        typeCode = IntimacyFields.TYPE_CODE,
        occurredAtEpochMillis = occurredMillis,
        localDate = occurredDate,
        zoneId = tokyo.id,
        note = "",
        createdAtEpochMillis = occurredMillis,
        updatedAtEpochMillis = occurredMillis
    )
}
