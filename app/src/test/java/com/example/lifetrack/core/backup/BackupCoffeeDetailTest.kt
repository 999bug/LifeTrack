package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.RecordDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** A7：咖啡 / 茶详情已注册规格后，生产默认解码必须放行且仍逐字段校验。 */
class BackupCoffeeDetailTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-09T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-09T06:00:00Z")

    private val validFields = mapOf(
        CoffeeFields.DRINK to "tea",
        CoffeeFields.AMOUNT to "300",
        CoffeeFields.UNIT to "ml",
        CoffeeFields.CAFFEINE to "no"
    )

    @Test
    fun `production defaults round trip coffee detail`() {
        val result = BackupCodec.decode(encode(validFields), fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val record = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals(CoffeeFields.TYPE_CODE, record.event.typeCode)
        assertEquals(RecordDetail.Generic(CoffeeFields.TYPE_CODE, validFields), record.detail)
    }

    @Test
    fun `rejects coffee amount out of range`() {
        listOf("0", "2001", "abc", "").forEach { value ->
            val fields = validFields + (CoffeeFields.AMOUNT to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(value, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `rejects an unknown coffee field`() {
        val fields = validFields + ("sugar" to "yes")
        val result = BackupCodec.decode(encode(fields), fixedNow)
        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects illegal enum values`() {
        listOf(
            CoffeeFields.DRINK to "juice",
            CoffeeFields.UNIT to "litre",
            CoffeeFields.CAFFEINE to "high"
        ).forEach { (key, value) ->
            val fields = validFields + (key to value)
            val result = BackupCodec.decode(encode(fields), fixedNow)
            assertEquals(key, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `rejects a detail type that disagrees with the event`() {
        val result = BackupCodec.decode(encode(validFields, detailType = "water"), fixedNow)
        assertEquals(BackupError.DETAIL_TYPE_MISMATCH, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `a coffee record without detail is still valid`() {
        val record = BackupRecord(event = event(), detail = null)
        val text = BackupCodec.encode(DEFAULT_TYPES, listOf(record), fixedNow)
        val result = BackupCodec.decode(text, fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        assertEquals(null, (result as BackupDecodeResult.Success).payload.events.single().detail)
    }

    @Test
    fun `rejects a coffee event when the type is missing from the table`() {
        val types = DEFAULT_TYPES.filterNot { it.code == CoffeeFields.TYPE_CODE }
        val text = BackupCodec.encode(
            types,
            listOf(BackupRecord(event = event(), detail = RecordDetail.Generic(CoffeeFields.TYPE_CODE, validFields))),
            fixedNow
        )
        val result = BackupCodec.decode(text, fixedNow)

        assertEquals(BackupError.UNKNOWN_TYPE, (result as BackupDecodeResult.Invalid).error)
    }

    private fun encode(
        fields: Map<String, String>,
        detailType: String = CoffeeFields.TYPE_CODE
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
        id = "c1",
        typeCode = CoffeeFields.TYPE_CODE,
        occurredAtEpochMillis = occurredMillis,
        localDate = occurredDate,
        zoneId = tokyo.id,
        note = "",
        createdAtEpochMillis = occurredMillis,
        updatedAtEpochMillis = occurredMillis
    )
}
