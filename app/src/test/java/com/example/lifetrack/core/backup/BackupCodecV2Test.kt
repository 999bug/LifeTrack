package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** 备份 v2 的格式、v1 兼容与通用详情校验链。 */
class BackupCodecV2Test {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-09T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-09T06:00:00Z")

    private val waterType = TrackTypeEntity(
        code = "water",
        name = "饮水",
        iconKey = "water",
        category = "intake",
        trackingMode = "EVENT",
        enabled = true,
        sortOrder = 20,
        schemaVersion = 1
    )

    private val waterTypesJson =
        """[{"code":"poop","name":"排便","iconKey":"poop","category":"body",""" +
            """"trackingMode":"EVENT","enabled":true,"sortOrder":10,"schemaVersion":1},""" +
            """{"code":"water","name":"饮水","iconKey":"water","category":"intake",""" +
            """"trackingMode":"EVENT","enabled":true,"sortOrder":20,"schemaVersion":1}]"""

    private val coffeeTypesJson = waterTypesJson.trimEnd(']') +
        """,{"code":"coffee","name":"咖啡","iconKey":"coffee","category":"intake",""" +
        """"trackingMode":"EVENT","enabled":true,"sortOrder":30,"schemaVersion":1}]"""

    private val waterOptions = BackupDecodeOptions(
        supportedDetailTypes = setOf("water"),
        detailSpecs = mapOf(
            "water" to DetailSpec(
                typeCode = "water",
                allowedFields = setOf("amountMl", "unit"),
                validators = mapOf(
                    "amountMl" to { it.toIntOrNull()?.let { v -> v in 1..5000 } == true },
                    "unit" to { it in setOf("ml", "cup") }
                )
            )
        )
    )

    @Test
    fun `exports schema version 2`() {
        val text = BackupCodec.encode(listOf(DEFAULT_POOP_TYPE), emptyList(), fixedNow)
        assertTrue(text.contains("\"schemaVersion\": 2"))
    }

    @Test
    fun `v1 backup still restores with poop detail`() {
        val legacy = backupJson(
            version = BackupCodec.LEGACY_SCHEMA_VERSION,
            events = poopEvent()
        )
        val result = BackupCodec.decode(legacy, fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val record = (result as BackupDecodeResult.Success).payload.events.single()
        val poop = record.detail as RecordDetail.Poop
        assertEquals(4, poop.bristolType)
        assertEquals(2, poop.amountCode)
        assertEquals("Asia/Tokyo", record.event.zoneId)
    }

    @Test
    fun `v2 round trip keeps generic detail`() {
        val record = BackupRecord(
            event = eventEntity(typeCode = "water"),
            detail = RecordDetail.Generic("water", mapOf("amountMl" to "250", "unit" to "ml"))
        )
        val text = BackupCodec.encode(listOf(DEFAULT_POOP_TYPE, waterType), listOf(record), fixedNow)
        val result = BackupCodec.decode(text, fixedNow, waterOptions)

        assertTrue(result is BackupDecodeResult.Success)
        val restored = (result as BackupDecodeResult.Success).payload.events.single()
        assertEquals(
            RecordDetail.Generic("water", mapOf("amountMl" to "250", "unit" to "ml")),
            restored.detail
        )
    }

    @Test
    fun `production defaults still reject unregistered detail types`() {
        val detail = """{"typeCode":"coffee","fields":{"cups":"1"}}"""
        val text = backupJson(
            typesJson = coffeeTypesJson,
            events = waterEvent(typeCode = "coffee", detail = detail)
        )
        val result = BackupCodec.decode(text, fixedNow)

        assertEquals(
            BackupError.UNSUPPORTED_DETAIL_TYPE,
            (result as BackupDecodeResult.Invalid).error
        )
    }

    @Test
    fun `rejects detail type that disagrees with the event`() {
        val detail = """{"typeCode":"coffee","fields":{"amountMl":"250"}}"""
        val result = BackupCodec.decode(backupJson(events = waterEvent(detail = detail)), fixedNow, waterOptions)

        assertEquals(BackupError.DETAIL_TYPE_MISMATCH, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects generic detail on a poop event`() {
        val detail = """{"typeCode":"poop","fields":{"amountMl":"250"}}"""
        val result = BackupCodec.decode(backupJson(events = poopEvent(detail)), fixedNow, waterOptions)

        assertEquals(BackupError.DETAIL_NOT_ALLOWED, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects poop detail on a non poop event`() {
        val poop = """{"bristolType":4}"""
        val result = BackupCodec.decode(
            backupJson(events = waterEvent(typeCode = "water", poop = poop)),
            fixedNow,
            waterOptions
        )

        assertEquals(BackupError.DETAIL_NOT_ALLOWED, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a v1 file carrying a v2 detail channel`() {
        val detail = """{"typeCode":"water","fields":{"amountMl":"250"}}"""
        val result = BackupCodec.decode(
            backupJson(version = 1, events = waterEvent(detail = detail)),
            fixedNow,
            waterOptions
        )

        assertEquals(BackupError.DETAIL_NOT_ALLOWED, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an event type missing from the type table`() {
        val result = BackupCodec.decode(
            backupJson(events = waterEvent(typeCode = "coffee")),
            fixedNow,
            waterOptions
        )

        assertEquals(BackupError.UNKNOWN_TYPE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects structurally invalid detail keys`() {
        val longKey = "x".repeat(33)
        val longValue = "x".repeat(257)
        val tooMany = (1..17).joinToString(",") { "\"k$it\":\"1\"" }
        val cases = listOf(
            """{"typeCode":"water","fields":{"AmountMl":"250"}}""",
            """{"typeCode":"water","fields":{"":"250"}}""",
            """{"typeCode":"water","fields":{"$longKey":"250"}}""",
            """{"typeCode":"water","fields":{"amountMl":"$longValue"}}""",
            """{"typeCode":"water","fields":{$tooMany}}"""
        )
        cases.forEach { detail ->
            val result = BackupCodec.decode(backupJson(events = waterEvent(detail = detail)), fixedNow, waterOptions)
            assertEquals(detail, BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
        }
    }

    @Test
    fun `rejects a field outside the registered spec`() {
        val detail = """{"typeCode":"water","fields":{"caffeine":"yes"}}"""
        val result = BackupCodec.decode(backupJson(events = waterEvent(detail = detail)), fixedNow, waterOptions)

        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a value failing the registered validator`() {
        val detail = """{"typeCode":"water","fields":{"amountMl":"99999"}}"""
        val result = BackupCodec.decode(backupJson(events = waterEvent(detail = detail)), fixedNow, waterOptions)

        assertEquals(BackupError.INVALID_DETAIL_FIELD, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `accepts a quick record without any detail`() {
        val text = backupJson(events = waterEvent(detail = null))
        val result = BackupCodec.decode(text, fixedNow, waterOptions)

        assertTrue(result is BackupDecodeResult.Success)
        assertEquals(null, (result as BackupDecodeResult.Success).payload.events.single().detail)
    }

    private fun eventEntity(typeCode: String) = TrackEventEntity(
        id = "e1",
        typeCode = typeCode,
        occurredAtEpochMillis = occurredMillis,
        localDate = occurredDate,
        zoneId = tokyo.id,
        note = "note",
        createdAtEpochMillis = occurredMillis,
        updatedAtEpochMillis = occurredMillis
    )

    private fun backupJson(
        version: Int = BackupCodec.SCHEMA_VERSION,
        events: String,
        eventCount: Int = 1,
        typesJson: String = waterTypesJson
    ): String = """
        {
          "app": "LifeTrack",
          "schemaVersion": $version,
          "exportedAt": "2026-11-09T05:00:00Z",
          "eventCount": $eventCount,
          "types": $typesJson,
          "events": [$events]
        }
    """.trimIndent()

    private fun poopEvent(detail: String? = null): String {
        val poop = """{"bristolType":4,"amountCode":2}"""
        val detailField = if (detail == null) "" else ""","detail":$detail"""
        return """{"id":"e1","typeCode":"poop","occurredAtEpochMillis":$occurredMillis,""" +
            """"localDate":"$occurredDate","zoneId":"Asia/Tokyo","note":"",""" +
            """"createdAtEpochMillis":$occurredMillis,"updatedAtEpochMillis":$occurredMillis,""" +
            """"poop":$poop$detailField}"""
    }

    private fun waterEvent(
        typeCode: String = "water",
        detail: String? = """{"typeCode":"water","fields":{"amountMl":"250"}}""",
        poop: String? = null
    ): String {
        val detailField = if (detail == null) "" else ""","detail":$detail"""
        val poopField = if (poop == null) "" else ""","poop":$poop"""
        return """{"id":"e1","typeCode":"$typeCode","occurredAtEpochMillis":$occurredMillis,""" +
            """"localDate":"$occurredDate","zoneId":"Asia/Tokyo","note":"",""" +
            """"createdAtEpochMillis":$occurredMillis,"updatedAtEpochMillis":$occurredMillis""" +
            """$poopField$detailField}"""
    }
}
