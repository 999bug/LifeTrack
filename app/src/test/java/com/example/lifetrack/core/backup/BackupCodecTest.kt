package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.model.PoopRecordRelation
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred: Instant = Instant.parse("2026-11-09T03:00:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()

    private val fixedNow: Instant = Instant.parse("2026-11-09T06:00:00Z")

    private val defaultTypesJson =
        """[{"code":"poop","name":"排便","iconKey":"poop","category":"body",""" +
            """"trackingMode":"EVENT","enabled":true,"sortOrder":10,"schemaVersion":1}]"""

    @Test
    fun `round trip keeps every field`() {
        val events = listOf(
            record(
                id = "e1",
                detail = PoopDetailEntity(
                    eventId = "e1",
                    bristolType = 4,
                    amountCode = 2,
                    effortCode = 1,
                    urgency = true,
                    abdominalDiscomfort = false,
                    incompleteEvacuation = null
                )
            ),
            record(id = "e2", detail = null)
        )

        val text = BackupCodec.encode(listOf(DEFAULT_POOP_TYPE), events, fixedNow)
        val result = BackupCodec.decode(text, fixedNow)

        assertTrue(result is BackupDecodeResult.Success)
        val payload = (result as BackupDecodeResult.Success).payload
        assertEquals(1, payload.types.size)
        assertEquals(2, payload.events.size)

        val first = payload.events.first()
        assertEquals("e1", first.event.id)
        assertEquals(occurredMillis, first.event.occurredAtEpochMillis)
        assertEquals(occurredDate, first.event.localDate)
        assertEquals("Asia/Tokyo", first.event.zoneId)
        assertEquals(4, first.detail?.bristolType)
        assertEquals(true, first.detail?.urgency)
        assertNull(first.detail?.incompleteEvacuation)

        assertNull(payload.events[1].detail)
    }

    @Test
    fun `rejects a file that is not json`() {
        val result = BackupCodec.decode("this is not json", fixedNow)
        assertEquals(BackupError.NOT_JSON, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a file from another app`() {
        val result = BackupCodec.decode(backupJson(app = "OtherApp"), fixedNow)
        assertEquals(BackupError.NOT_LIFETRACK_FILE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an unknown schema version`() {
        val result = BackupCodec.decode(backupJson(schemaVersion = 2), fixedNow)
        assertEquals(BackupError.UNSUPPORTED_VERSION, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a tampered event count`() {
        val result = BackupCodec.decode(backupJson(eventCount = 5), fixedNow)
        assertEquals(BackupError.EVENT_COUNT_MISMATCH, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects duplicate event ids`() {
        val events = "[${event("dup")},${event("dup")}]"
        val result = BackupCodec.decode(backupJson(eventCount = 2, eventsJson = events), fixedNow)
        assertEquals(BackupError.DUPLICATE_EVENT_ID, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an event type missing from the type table`() {
        val result = BackupCodec.decode(backupJson(eventsJson = "[${event("e1", typeCode = "coffee")}]"), fixedNow)
        assertEquals(BackupError.UNKNOWN_TYPE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a backup without the builtin type`() {
        val coffeeTypes =
            """[{"code":"coffee","name":"咖啡","iconKey":"coffee","category":"intake",""" +
                """"trackingMode":"EVENT","enabled":true,"sortOrder":20,"schemaVersion":1}]"""
        val result = BackupCodec.decode(
            backupJson(typesJson = coffeeTypes, eventsJson = "[${event("e1", typeCode = "coffee")}]"),
            fixedNow
        )
        assertEquals(BackupError.MISSING_BASE_TYPE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects an invalid zone`() {
        val result = BackupCodec.decode(backupJson(eventsJson = "[${event("e1", zoneId = "Mars/Base")}]"), fixedNow)
        assertEquals(BackupError.INVALID_ZONE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a local date that contradicts the instant`() {
        val result = BackupCodec.decode(backupJson(eventsJson = "[${event("e1", localDate = "2020-01-01")}]"), fixedNow)
        assertEquals(BackupError.LOCAL_DATE_MISMATCH, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a bristol type out of range`() {
        val result = BackupCodec.decode(backupJson(eventsJson = "[${event("e1", bristol = 8)}]"), fixedNow)
        assertEquals(BackupError.INVALID_BRISTOL, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `rejects a timestamp far in the future`() {
        val future = fixedNow.plusSeconds(400L * 24 * 3600).toEpochMilli()
        val result = BackupCodec.decode(backupJson(eventsJson = "[${event("e1", millis = future)}]"), fixedNow)
        assertEquals(BackupError.INVALID_TIMESTAMP, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun `tolerates one day of timezone database drift`() {
        val drifted = occurred.atZone(tokyo).toLocalDate().plusDays(1).toString()
        val result = BackupCodec.decode(backupJson(eventsJson = "[${event("e1", localDate = drifted)}]"), fixedNow)
        assertTrue(result is BackupDecodeResult.Success)
    }

    @Test
    fun `backup io refuses oversized input`() {
        val payload = "x".repeat(2048)
        assertNotNull(BackupIo.readUtf8(ByteArrayInputStream(payload.toByteArray()), limit = 4096))
        assertNull(BackupIo.readUtf8(ByteArrayInputStream(payload.toByteArray()), limit = 1024))
    }

    private fun backupJson(
        app: String = "LifeTrack",
        schemaVersion: Int = 1,
        eventCount: Int = 1,
        typesJson: String = defaultTypesJson,
        eventsJson: String = "[${event("e1")}]"
    ): String = """
        {
          "app": "$app",
          "schemaVersion": $schemaVersion,
          "exportedAt": "2026-11-09T05:00:00Z",
          "eventCount": $eventCount,
          "types": $typesJson,
          "events": $eventsJson
        }
    """.trimIndent()

    private fun event(
        id: String,
        typeCode: String = "poop",
        millis: Long = occurredMillis,
        localDate: String = occurredDate,
        zoneId: String = "Asia/Tokyo",
        bristol: Int? = 3
    ): String {
        val poop = if (bristol == null) "null" else """{"bristolType":$bristol}"""
        return """{"id":"$id","typeCode":"$typeCode","occurredAtEpochMillis":$millis,""" +
            """"localDate":"$localDate","zoneId":"$zoneId","note":"",""" +
            """"createdAtEpochMillis":$millis,"updatedAtEpochMillis":$millis,"poop":$poop}"""
    }

    private fun record(id: String, detail: PoopDetailEntity?): PoopRecordRelation = PoopRecordRelation(
        event = TrackEventEntity(
            id = id,
            typeCode = "poop",
            occurredAtEpochMillis = occurredMillis,
            localDate = occurredDate,
            zoneId = tokyo.id,
            note = "note-$id",
            createdAtEpochMillis = occurredMillis,
            updatedAtEpochMillis = occurredMillis
        ),
        detail = detail
    )
}
