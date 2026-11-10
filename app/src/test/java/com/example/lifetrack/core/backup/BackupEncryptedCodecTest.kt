package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEncryptedCodecTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val occurred: Instant = Instant.parse("2026-11-10T03:00:00Z")
    private val fixedNow: Instant = Instant.parse("2026-11-10T06:00:00Z")
    private val passphrase = "life-track-2026".toCharArray()

    private val record = BackupRecord(
        event = TrackEventEntity(
            id = "e1",
            typeCode = "poop",
            occurredAtEpochMillis = occurred.toEpochMilli(),
            localDate = occurred.atZone(tokyo).toLocalDate().toString(),
            zoneId = tokyo.id,
            note = "secret-note-value",
            createdAtEpochMillis = occurred.toEpochMilli(),
            updatedAtEpochMillis = occurred.toEpochMilli()
        ),
        detail = RecordDetail.Poop(bristolType = 4, amountCode = 2, effortCode = 1)
    )

    @Test
    fun encryptedRoundTrip_keepsPayload() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)

        val textWithoutPassphrase = BackupCodec.decode(text, fixedNow)
        assertEquals(BackupDecodeResult.PassphraseRequired, textWithoutPassphrase)

        val result = BackupCodec.decode(text, fixedNow, passphrase = passphrase)
        assertTrue(result is BackupDecodeResult.Success)
        val success = result as BackupDecodeResult.Success
        val restored = success.payload.events.single()
        assertEquals("e1", restored.event.id)
        assertEquals("secret-note-value", restored.event.note)
        assertEquals(RecordDetail.Poop(bristolType = 4, amountCode = 2, effortCode = 1), restored.detail)
        assertEquals(1, success.payload.types.size)
    }

    @Test
    fun encryptedFile_declaresV3AndHidesContent() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)
        val json = Json.parseToJsonElement(text).jsonObject

        assertEquals("LifeTrack", json["app"]!!.jsonPrimitive.content)
        assertEquals("3", json["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals("PBKDF2WithHmacSHA256", json["kdf"]!!.jsonPrimitive.content)
        assertEquals("AES-256-GCM", json["cipher"]!!.jsonPrimitive.content)
        assertEquals("210000", json["iterations"]!!.jsonPrimitive.content)
        assertTrue(!text.contains("secret-note-value"))
        assertTrue(!text.contains("\"events\""))
    }

    @Test
    fun encryptedFile_requiresPassphrase() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)
        assertEquals(BackupDecodeResult.PassphraseRequired, BackupCodec.decode(text, fixedNow, passphrase = null))
    }

    @Test
    fun wrongPassphrase_isRejected() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)
        val result = BackupCodec.decode(text, fixedNow, passphrase = "another-passphrase".toCharArray())
        assertEquals(BackupError.WRONG_PASSPHRASE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun tamperedCiphertext_isRejected() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)
        val payload = Json.parseToJsonElement(text).jsonObject["payload"]!!.jsonPrimitive.content
        val tampered = payload.substring(0, 8) + flip(payload[8]) + payload.substring(9)

        val result = BackupCodec.decode(text.replace(payload, tampered), fixedNow, passphrase = passphrase)
        assertEquals(BackupError.WRONG_PASSPHRASE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun tamperedHeader_isRejected() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)
        // 只改导出时间，结构完全合法；AAD 绑定头部后必须解不开。
        val tampered = text.replace(fixedNow.toString(), "2030-01-01T00:00:00Z")
        assertTrue(tampered != text)

        val result = BackupCodec.decode(tampered, fixedNow, passphrase = passphrase)
        assertEquals(BackupError.WRONG_PASSPHRASE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun unsupportedIterations_areRejectedBeforeKdf() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)
        val tampered = text.replace("\"iterations\": 210000", "\"iterations\": 1000")
        assertTrue(tampered != text)

        val result = BackupCodec.decode(tampered, fixedNow, passphrase = passphrase)
        assertEquals(BackupError.UNSUPPORTED_ENCRYPTION, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun truncatedPayload_isRejectedAsEnvelopeDamage() {
        val text = BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, fixedNow)
        val payload = Json.parseToJsonElement(text).jsonObject["payload"]!!.jsonPrimitive.content
        val truncated = text.replace(payload, payload.substring(0, 8))

        val result = BackupCodec.decode(truncated, fixedNow, passphrase = passphrase)
        assertEquals(BackupError.INVALID_ENVELOPE, (result as BackupDecodeResult.Invalid).error)
    }

    /** 兼容性红线：加密能力上线后，0.5.0 及更早版本的明文备份必须照样能恢复。 */
    @Test
    fun plaintextV2Backup_stillDecodes() {
        val text = BackupCodec.encode(listOf(DEFAULT_POOP_TYPE), listOf(record), fixedNow)
        val result = BackupCodec.decode(text, fixedNow)
        assertTrue(result is BackupDecodeResult.Success)
        assertEquals(1, (result as BackupDecodeResult.Success).payload.events.size)
    }

    @Test
    fun v3WithoutPayload_isDamagedEnvelope() {
        val text = """
            {"app":"LifeTrack","schemaVersion":3,"exportedAt":"2026-11-10T06:00:00Z",
             "eventCount":0,"types":[],"events":[]}
        """.trimIndent()
        val result = BackupCodec.decode(text, fixedNow, passphrase = passphrase)
        assertEquals(BackupError.INVALID_ENVELOPE, (result as BackupDecodeResult.Invalid).error)
    }

    private fun flip(c: Char): Char = if (c == 'A') 'B' else 'A'
}
