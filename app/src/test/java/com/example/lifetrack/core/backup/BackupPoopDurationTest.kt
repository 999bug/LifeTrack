package com.example.lifetrack.core.backup

import com.example.lifetrack.core.model.RecordDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 排便用时（v4 新增两列）的备份往返与校验。
 *
 * 老备份没有 `endedAtEpochMillis` / `durationMinutes`，必须照旧可读；
 * 新字段的组合不合法时整份备份拒绝，而不是把异常值静默写进库。
 */
class BackupPoopDurationTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred = Instant.parse("2026-11-16T02:26:00Z")
    private val occurredMillis = occurred.toEpochMilli()
    private val occurredDate = occurred.atZone(tokyo).toLocalDate().toString()
    private val fixedNow = Instant.parse("2026-11-16T06:00:00Z")

    private fun poopJson(
        endedAt: Long? = null,
        duration: Int? = null,
        extra: String = ""
    ): String = buildString {
        append("""{"bristolType":4,"amountCode":2""")
        endedAt?.let { append(""","endedAtEpochMillis":$it""") }
        duration?.let { append(""","durationMinutes":$it""") }
        if (extra.isNotEmpty()) append(",$extra")
        append("}")
    }

    private fun fileJson(poop: String): String =
        """{"app":"LifeTrack","schemaVersion":2,"exportedAt":"2026-11-16T06:00:00Z",""" +
            """"eventCount":1,"types":[{"code":"poop","name":"排便","iconKey":"poop","category":"body",""" +
            """"trackingMode":"EVENT","enabled":true,"sortOrder":10,"schemaVersion":1}],""" +
            """"events":[{"id":"e1","typeCode":"poop","occurredAtEpochMillis":$occurredMillis,""" +
            """"localDate":"$occurredDate","zoneId":"Asia/Tokyo","note":"",""" +
            """"createdAtEpochMillis":$occurredMillis,"updatedAtEpochMillis":$occurredMillis,""" +
            """"poop":$poop}]}"""

    private fun decode(poop: String) = BackupCodec.decode(fileJson(poop), fixedNow)

    @Test
    fun `records without duration keep decoding as before`() {
        val result = decode(poopJson())
        assertTrue(result is BackupDecodeResult.Success)
        val detail = (result as BackupDecodeResult.Success).payload.events.single().detail
        assertEquals(RecordDetail.Poop(bristolType = 4, amountCode = 2), detail)
    }

    @Test
    fun `end time and duration round trip`() {
        val ended = occurredMillis + 12 * 60_000L
        val result = decode(poopJson(endedAt = ended, duration = 12))
        assertTrue(result is BackupDecodeResult.Success)
        val detail = (result as BackupDecodeResult.Success).payload.events.single().detail as RecordDetail.Poop
        assertEquals(12, detail.durationMinutes)
        assertEquals(ended, detail.endedAtEpochMillis)
    }

    @Test
    fun `manual duration alone is accepted`() {
        val result = decode(poopJson(duration = 25))
        assertTrue(result is BackupDecodeResult.Success)
        val detail = (result as BackupDecodeResult.Success).payload.events.single().detail as RecordDetail.Poop
        assertEquals(25, detail.durationMinutes)
        assertEquals(null, detail.endedAtEpochMillis)
    }

    @Test
    fun `duration outside one to sixty minutes is rejected`() {
        listOf(0, 61, 1440).forEach { value ->
            val result = decode(poopJson(duration = value))
            assertEquals(
                value.toString(),
                BackupError.INVALID_DURATION,
                (result as BackupDecodeResult.Invalid).error
            )
        }
    }

    @Test
    fun `end time without duration is rejected`() {
        val result = decode(poopJson(endedAt = occurredMillis + 5 * 60_000L))
        assertEquals(
            BackupError.INVALID_DURATION,
            (result as BackupDecodeResult.Invalid).error
        )
    }

    @Test
    fun `end time before start is rejected`() {
        val result = decode(poopJson(endedAt = occurredMillis - 60_000L, duration = 3))
        assertEquals(
            BackupError.INVALID_DURATION,
            (result as BackupDecodeResult.Invalid).error
        )
    }

    @Test
    fun `end time beyond the threshold is rejected`() {
        val result = decode(poopJson(endedAt = occurredMillis + 61 * 60_000L, duration = 5))
        assertEquals(
            BackupError.INVALID_DURATION,
            (result as BackupDecodeResult.Invalid).error
        )
    }

    @Test
    fun `duration must agree with the stored end time`() {
        val result = decode(poopJson(endedAt = occurredMillis + 20 * 60_000L, duration = 40))
        assertEquals(
            BackupError.INVALID_DURATION,
            (result as BackupDecodeResult.Invalid).error
        )
    }

    @Test
    fun `export keeps duration fields`() {
        // 备份必须声明基础类型，否则解码侧会按 MISSING_BASE_TYPE 拒绝。
        val payload = BackupPayload(
            types = listOf(com.example.lifetrack.core.database.DEFAULT_POOP_TYPE),
            events = listOf(
                com.example.lifetrack.core.model.BackupRecord(
                    event = com.example.lifetrack.core.database.entity.TrackEventEntity(
                        id = "e1",
                        typeCode = "poop",
                        occurredAtEpochMillis = occurredMillis,
                        localDate = occurredDate,
                        zoneId = "Asia/Tokyo",
                        note = "",
                        createdAtEpochMillis = occurredMillis,
                        updatedAtEpochMillis = occurredMillis
                    ),
                    detail = RecordDetail.Poop(
                        bristolType = 4,
                        endedAtEpochMillis = occurredMillis + 8 * 60_000L,
                        durationMinutes = 8
                    )
                )
            )
        )
        val text = BackupCodec.encode(payload.types, payload.events, fixedNow)
        val roundTrip = BackupCodec.decode(text, fixedNow)
        assertTrue(roundTrip is BackupDecodeResult.Success)
        val detail = (roundTrip as BackupDecodeResult.Success)
            .payload.events.single().detail as RecordDetail.Poop
        assertEquals(8, detail.durationMinutes)
        assertEquals(occurredMillis + 8 * 60_000L, detail.endedAtEpochMillis)
    }
}
