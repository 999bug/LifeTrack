package com.example.lifetrack.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PassphraseEnvelopeTest {

    private val iv = ByteArray(PassphraseEnvelope.IV_LENGTH) { (it + 1).toByte() }
    private val ciphertext = ByteArray(PassphraseEnvelope.MIN_CIPHERTEXT_LENGTH) { (it * 7).toByte() }

    @Test
    fun encodeDecode_keepsIvAndCiphertext() {
        val text = PassphraseEnvelope.encode(iv, ciphertext)
        assertTrue(text.startsWith("lt-enc-v1:"))
        val restored = PassphraseEnvelope.decode(text)
        assertNotNull(restored)
        assertArrayEquals(iv, restored!!.iv)
        assertArrayEquals(ciphertext, restored.ciphertext)
    }

    @Test
    fun decode_rejectsMalformedEnvelope() {
        val valid = PassphraseEnvelope.encode(iv, ciphertext)
        val b64Cipher = Base64.getEncoder().encodeToString(ciphertext)

        val invalid = listOf(
            "",
            "lt-enc-v2:${Base64.getEncoder().encodeToString(iv)}:$b64Cipher",
            valid.removeSuffix(b64Cipher),                       // 少一段
            valid + ":extra",                                     // 多一段
            "lt-enc-v1:not-base64!:$b64Cipher",                   // IV 非法 Base64
            "lt-enc-v1:${Base64.getEncoder().encodeToString(iv)}:not-base64!",
            "lt-enc-v1:${Base64.getEncoder().encodeToString(ByteArray(8))}:$b64Cipher", // IV 长度错
            "lt-enc-v1:${Base64.getEncoder().encodeToString(iv)}:${Base64.getEncoder().encodeToString(ByteArray(16))}"
        )
        invalid.forEachIndexed { index, text ->
            assertNull("case $index should be rejected: $text", PassphraseEnvelope.decode(text))
        }
    }

    @Test
    fun encode_rejectsWrongIvOrShortCiphertext() {
        assertThrows(IllegalArgumentException::class.java) {
            PassphraseEnvelope.encode(ByteArray(16), ciphertext)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PassphraseEnvelope.encode(iv, ByteArray(16))
        }
    }

    @Test
    fun envelope_neverContainsPassphrasePlaintext() {
        val passphrase = "THIS_MUST_NOT_APPEAR_IN_ENVELOPE".toByteArray()
        val sealed = encrypt(newKey(), passphrase)
        val text = PassphraseEnvelope.encode(sealed.first, sealed.second)
        assertTrue(!text.contains(String(passphrase)))
        assertEquals(passphrase.size + PassphraseEnvelope.GCM_TAG_LENGTH, sealed.second.size)
    }

    /** 完整性由 AES-GCM 保证，信封层只做结构校验：篡改后的密文仍能解析，但解密必须失败。 */
    @Test
    fun gcm_rejectsTamperedCiphertext() {
        val key = newKey()
        val passphrase = ByteArray(PassphraseEnvelope.PASSPHRASE_LENGTH) { 42 }
        val (nonce, sealedBytes) = encrypt(key, passphrase)
        val text = PassphraseEnvelope.encode(nonce, sealedBytes)

        val tampered = sealedBytes.clone().also { it[3] = (it[3].toInt() xor 0x01).toByte() }
        val tamperedText = PassphraseEnvelope.encode(nonce, tampered)
        assertNotNull(PassphraseEnvelope.decode(tamperedText))
        assertNotNull(PassphraseEnvelope.decode(text))

        assertThrows(Exception::class.java) {
            decrypt(key, nonce, tampered)
        }
        assertArrayEquals(passphrase, decrypt(key, nonce, sealedBytes))
    }

    @Test
    fun gcm_rejectsWrongKey() {
        val passphrase = ByteArray(PassphraseEnvelope.PASSPHRASE_LENGTH) { 7 }
        val (nonce, sealedBytes) = encrypt(newKey(), passphrase)
        assertThrows(Exception::class.java) {
            decrypt(newKey(), nonce, sealedBytes)
        }
    }

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun encrypt(key: SecretKey, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv to cipher.doFinal(plaintext)
    }

    private fun decrypt(key: SecretKey, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext)
    }
}
