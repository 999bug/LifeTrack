package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.RecordDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** A9：月经详情注册规格后，生产默认解码必须放行且仍逐字段校验（含症状白名单 / 上限）。 */
class BackupMenstruationDetailTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-09T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-09T06:00:00Z")

    private val validFields = mapOf(
        MenstruationFields.FLOW to "medium",
        MenstruationFields.PAIN to "mild",
        MenstruationFields.SYMPTOMS to "cramps|backache",
        MenstruationFields.MOOD to "low",
        MenstruationFields.DISCHARGE to "eggwhite"
    )

    @Test
    fun `production defaults round trip menstruation detail`() {
        val result = BackupCodec.decode(encode(validFields), fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val record = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals(MenstruationFields.TYPE_CODE, record.event.typeCode)
        assertEquals(RecordDetail.Generic(MenstruationFields.TYPE_CODE, validFields), record.detail)
    }

    @Test
    fun `partial fields are valid because all five are optional`() {
        listOf(
            emptyMap(),
            mapOf(MenstruationFields.FLOW to "spotting"),
            mapOf(MenstruationFields.SYMPTOMS to "fatigue"),
            mapOf(MenstruationFields.MOOD to "calm"),
            mapOf(MenstruationFields.DISCHARGE to "dry")
        ).forEach { fields ->
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertTrue(fields.toString(), result is BackupDecodeResult.Success)
        }
    }

    @Test
    fun `eight symptoms round trip at the multi select cap`() {
        val eight = MenstruationFields.SYMPTOM_CODES.take(MenstruationFields.MAX_SYMPTOMS)
        val fields = mapOf(MenstruationFields.SYMPTOMS to MenstruationFields.encodeSymptoms(eight))
        val result = BackupCodec.decode(encode(fields), fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val detail = (result as BackupDecodeResult.Success).payload.events.single().detail
        assertEquals(RecordDetail.Generic(MenstruationFields.TYPE_CODE, fields), detail)
    }

    @Test
    fun `diary travels in note and survives the round trip`() {
        val record = BackupRecord(
            event = event(note = "今天有点累，请勿记录他人身份信息"),
            detail = RecordDetail.Generic(MenstruationFields.TYPE_CODE, validFields)
        )
        val text = BackupCodec.encode(DEFAULT_TYPES, listOf(record), fixedNow)
        val result = BackupCodec.decode(text, fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val decoded = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals("今天有点累，请勿记录他人身份信息", decoded.event.note)
        assertEquals(RecordDetail.Generic(MenstruationFields.TYPE_CODE, validFields), decoded.detail)
    }

    @Test
    fun `rejects illegal flow pain mood or discharge values`() {
        listOf(
            MenstruationFields.FLOW to "gushing",
            MenstruationFields.PAIN to "unbearable",
            MenstruationFields.MOOD to "ecstatic",
            MenstruationFields.DISCHARGE to "bloody"
        ).forEach { (key, value) ->
            val fields = validFields + (key to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(key, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `rejects symptoms outside the whitelist`() {
        val fields = validFields + (MenstruationFields.SYMPTOMS to "cramps|fever")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects duplicate symptoms`() {
        val fields = validFields + (MenstruationFields.SYMPTOMS to "cramps|cramps")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects more than eight symptoms`() {
        val nine = MenstruationFields.SYMPTOM_CODES.take(MenstruationFields.MAX_SYMPTOMS + 1)
        val fields = validFields +
            (MenstruationFields.SYMPTOMS to MenstruationFields.encodeSymptoms(nine))
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an empty symptoms value`() {
        val fields = validFields + (MenstruationFields.SYMPTOMS to "")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an overlong symptoms value`() {
        val fields = validFields + (MenstruationFields.SYMPTOMS to "x".repeat(257))
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an unknown field such as a partner identity or weight`() {
        listOf("partnerName" to "someone", "weightKg" to "55").forEach { (key, value) ->
            val fields = validFields + (key to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(key, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `a menstruation record without detail is still valid`() {
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
                detail = RecordDetail.Generic(MenstruationFields.TYPE_CODE, fields)
            )
        ),
        now = fixedNow
    )

    private fun event(note: String = "") = TrackEventEntity(
        id = "m1",
        typeCode = MenstruationFields.TYPE_CODE,
        occurredAtEpochMillis = occurredMillis,
        localDate = occurredDate,
        zoneId = tokyo.id,
        note = note,
        createdAtEpochMillis = occurredMillis,
        updatedAtEpochMillis = occurredMillis
    )
}