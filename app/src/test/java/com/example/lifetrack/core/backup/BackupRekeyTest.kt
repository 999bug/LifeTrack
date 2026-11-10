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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRekeyTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val occurred: Instant = Instant.parse("2026-11-10T03:00:00Z")
    private val now: Instant = Instant.parse("2026-11-10T06:00:00Z")
    private val oldPassphrase = "old-passphrase-2026".toCharArray()
    private val newPassphrase = "new-passphrase-2026".toCharArray()

    private val record = BackupRecord(
        event = TrackEventEntity(
            id = "e1",
            typeCode = "poop",
            occurredAtEpochMillis = occurred.toEpochMilli(),
            localDate = occurred.atZone(zone).toLocalDate().toString(),
            zoneId = zone.id,
            note = "rekey-note",
            createdAtEpochMillis = occurred.toEpochMilli(),
            updatedAtEpochMillis = occurred.toEpochMilli()
        ),
        detail = RecordDetail.Poop(bristolType = 3, amountCode = 1, effortCode = 2)
    )

    @Test
    fun rekey_makesOldPassphraseObsoleteAndKeepsPayload() {
        val original = backup(oldPassphrase)
        val result = BackupCodec.reencrypt(original, oldPassphrase, newPassphrase, now)

        assertTrue(result is BackupReencryptResult.Success)
        val rekeyed = (result as BackupReencryptResult.Success).text

        assertEquals(
            BackupError.WRONG_PASSPHRASE,
            (BackupCodec.decode(rekeyed, now, passphrase = oldPassphrase) as BackupDecodeResult.Invalid).error
        )
        val decoded = BackupCodec.decode(rekeyed, now, passphrase = newPassphrase)
        assertTrue(decoded is BackupDecodeResult.Success)
        val payload = (decoded as BackupDecodeResult.Success).payload
        assertEquals(1, payload.events.size)
        assertEquals("rekey-note", payload.events.single().event.note)
        assertEquals(RecordDetail.Poop(bristolType = 3, amountCode = 1, effortCode = 2), payload.events.single().detail)
    }

    @Test
    fun rekey_rejectsWrongOldPassphrase() {
        val original = backup(oldPassphrase)
        val result = BackupCodec.reencrypt(original, "wrong-old-pass".toCharArray(), newPassphrase, now)
        assertEquals(
            BackupError.WRONG_PASSPHRASE,
            (result as BackupReencryptResult.Invalid).error
        )
    }

    @Test
    fun rekey_rejectsPlaintextBackup() {
        val plain = BackupCodec.encode(listOf(DEFAULT_POOP_TYPE), listOf(record), now)
        val result = BackupCodec.reencrypt(plain, oldPassphrase, newPassphrase, now)
        assertEquals(
            BackupError.NOT_ENCRYPTED_BACKUP,
            (result as BackupReencryptResult.Invalid).error
        )
    }

    @Test
    fun rekey_rejectsDamagedEnvelope() {
        val original = backup(oldPassphrase)
        val payload = Json.parseToJsonElement(original).jsonObject["payload"]!!.jsonPrimitive.content
        val damaged = original.replace(payload, "")
        val result = BackupCodec.reencrypt(damaged, oldPassphrase, newPassphrase, now)
        assertEquals(
            BackupError.INVALID_ENVELOPE,
            (result as BackupReencryptResult.Invalid).error
        )
    }

    /** 每次轮换都要换新的盐与 IV，不能把旧密文原样搬过来。 */
    @Test
    fun rekey_usesFreshSaltAndIv() {
        val original = backup(oldPassphrase)
        val rekeyed = (BackupCodec.reencrypt(original, oldPassphrase, newPassphrase, now)
            as BackupReencryptResult.Success).text

        val before = Json.parseToJsonElement(original).jsonObject
        val after = Json.parseToJsonElement(rekeyed).jsonObject
        assertNotEquals(before["salt"]!!.jsonPrimitive.content, after["salt"]!!.jsonPrimitive.content)
        assertNotEquals(before["iv"]!!.jsonPrimitive.content, after["iv"]!!.jsonPrimitive.content)
        assertNotEquals(before["payload"]!!.jsonPrimitive.content, after["payload"]!!.jsonPrimitive.content)
    }

    /** 头部任何字段被改都必须解不开；盐参与 AAD，也要覆盖到。 */
    @Test
    fun tamperedSalt_isRejected() {
        val original = backup(oldPassphrase)
        val salt = Json.parseToJsonElement(original).jsonObject["salt"]!!.jsonPrimitive.content
        val tamperedSalt = (if (salt[0] == 'A') "B" else "A") + salt.substring(1)
        val tampered = original.replace(salt, tamperedSalt)

        val result = BackupCodec.decode(tampered, now, passphrase = oldPassphrase)
        assertEquals(BackupError.WRONG_PASSPHRASE, (result as BackupDecodeResult.Invalid).error)
    }

    @Test
    fun tamperedExportTime_isRejected() {
        val original = backup(oldPassphrase)
        val tampered = original.replace(now.toString(), "2030-01-01T00:00:00Z")
        assertTrue(tampered != original)

        val result = BackupCodec.decode(tampered, now, passphrase = oldPassphrase)
        assertEquals(BackupError.WRONG_PASSPHRASE, (result as BackupDecodeResult.Invalid).error)
    }

    private fun backup(passphrase: CharArray): String =
        BackupCodec.encodeEncrypted(listOf(DEFAULT_POOP_TYPE), listOf(record), passphrase, now)
}
