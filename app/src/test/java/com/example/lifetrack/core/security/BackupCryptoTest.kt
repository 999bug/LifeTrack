package com.example.lifetrack.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCryptoTest {

    private val passphrase = "correct horse battery staple".toCharArray()
    private val salt = ByteArray(BackupCrypto.SALT_LENGTH) { (it + 1).toByte() }
    private val iv = ByteArray(BackupCrypto.IV_LENGTH) { (it + 11).toByte() }
    private val aad = "LifeTrack|3|2026-11-10T00:00:00Z|kdf|210000|salt|iv|cipher".toByteArray()
    private val plaintext = """{"app":"LifeTrack","schemaVersion":2}""".toByteArray()

    @Test
    fun sealAndOpen_roundTrip() {
        val ciphertext = seal()
        assertArrayEquals(plaintext, open(ciphertext))
    }

    @Test
    fun open_rejectsWrongPassphrase() {
        val ciphertext = seal()
        val failure = failureOf {
            BackupCrypto.open(ciphertext, "wrong passphrase".toCharArray(), salt, iv, iterations(), aad)
        }
        assertEquals(BackupCrypto.Reason.DECRYPT_FAILED, failure.reason)
    }

    @Test
    fun open_rejectsTamperedCiphertext() {
        val tampered = seal().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
        assertEquals(BackupCrypto.Reason.DECRYPT_FAILED, failureOf { open(tampered) }.reason)
    }

    @Test
    fun open_rejectsTamperedAad() {
        val ciphertext = seal()
        val failure = failureOf {
            BackupCrypto.open(ciphertext, passphrase, salt, iv, iterations(), aad + "x".toByteArray())
        }
        assertEquals(BackupCrypto.Reason.DECRYPT_FAILED, failure.reason)
    }

    @Test
    fun open_rejectsTruncatedCiphertext() {
        val truncated = seal().copyOf(BackupCrypto.GCM_TAG_LENGTH - 1)
        assertEquals(
            BackupCrypto.Reason.CIPHERTEXT_TOO_SHORT,
            failureOf { open(truncated) }.reason
        )
    }

    @Test
    fun seal_rejectsBadParameters() {
        assertEquals(
            BackupCrypto.Reason.EMPTY_PASSPHRASE,
            failureOf {
                BackupCrypto.seal(plaintext, CharArray(0), salt, iv, iterations(), aad)
            }.reason
        )
        assertEquals(
            BackupCrypto.Reason.EMPTY_PASSPHRASE,
            failureOf {
                BackupCrypto.seal(plaintext, "   ".toCharArray(), salt, iv, iterations(), aad)
            }.reason
        )
        assertEquals(
            BackupCrypto.Reason.INVALID_ITERATIONS,
            failureOf {
                BackupCrypto.seal(plaintext, passphrase, salt, iv, BackupCrypto.MIN_ITERATIONS - 1, aad)
            }.reason
        )
        assertEquals(
            BackupCrypto.Reason.INVALID_ITERATIONS,
            failureOf {
                BackupCrypto.seal(plaintext, passphrase, salt, iv, BackupCrypto.MAX_ITERATIONS + 1, aad)
            }.reason
        )
        assertEquals(
            BackupCrypto.Reason.INVALID_SALT,
            failureOf {
                BackupCrypto.seal(plaintext, passphrase, ByteArray(8), iv, iterations(), aad)
            }.reason
        )
        assertEquals(
            BackupCrypto.Reason.INVALID_IV,
            failureOf {
                BackupCrypto.seal(plaintext, passphrase, salt, ByteArray(16), iterations(), aad)
            }.reason
        )
    }

    @Test
    fun seal_rejectsOversizedPlaintext() {
        val tooLarge = ByteArray(BackupCrypto.MAX_PLAINTEXT_BYTES + 1)
        assertEquals(
            BackupCrypto.Reason.PLAINTEXT_TOO_LARGE,
            failureOf {
                BackupCrypto.seal(tooLarge, passphrase, salt, iv, iterations(), aad)
            }.reason
        )
    }

    /** 每次加密都用新的盐与 IV：同一份明文、同一个口令，密文必须不同。 */
    @Test
    fun seal_usesFreshSaltAndIv() {
        val first = seal()
        val second = BackupCrypto.seal(
            plaintext,
            passphrase,
            BackupCrypto.newSalt(),
            BackupCrypto.newIv(),
            iterations(),
            aad
        )
        assert(!first.contentEquals(second))
    }

    private fun seal(): ByteArray =
        BackupCrypto.seal(plaintext, passphrase, salt, iv, iterations(), aad)

    private fun open(ciphertext: ByteArray): ByteArray =
        BackupCrypto.open(ciphertext, passphrase, salt, iv, iterations(), aad)

    private fun iterations() = BackupCrypto.MIN_ITERATIONS

    private fun failureOf(block: () -> Unit): BackupCrypto.Failure =
        try {
            block()
            throw AssertionError("expected BackupCrypto.Failure")
        } catch (e: BackupCrypto.Failure) {
            e
        }
}
