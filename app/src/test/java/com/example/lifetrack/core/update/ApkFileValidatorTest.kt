package com.example.lifetrack.core.update

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFileValidatorTest {

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun apkBytes(): ByteArray = ByteArray(1_000_100).apply {
        this[0] = 0x50
        this[1] = 0x4B
        this[2] = 0x03
        this[3] = 0x04
    }

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("lifetrack-apk-", ".tmp").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun acceptsZipMagicWithMatchingDigest() {
        val bytes = apkBytes()

        ApkFileValidator.validate(tempFile(bytes), sha256(bytes))
    }

    @Test
    fun rejectsChecksumMismatch() {
        val bytes = apkBytes()

        val error = assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.validate(tempFile(bytes), "0".repeat(64))
        }
        assertTrue(error.message.orEmpty().contains("checksum mismatch"))
    }

    @Test
    fun rejectsHtmlEvenWhenBodyIsLarge() {
        val bytes = ByteArray(1_000_001).also {
            "<!DOCTYPE html><html>login</html>".toByteArray().copyInto(it)
        }

        val error = assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.validate(tempFile(bytes), sha256(bytes))
        }
        assertTrue(error.message.orEmpty().contains("ZIP magic"))
    }

    @Test
    fun rejectsTinyFileEvenWithZipMagic() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        val error = assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.validate(tempFile(bytes), sha256(bytes))
        }
        assertTrue(error.message.orEmpty().contains("too small"))
    }

    @Test
    fun parsesStandardSha256sumOutput() {
        val hex = "03c037e470d5433ecbe6ef5e78040855adb021e656702a20f64a358ac81c6aff"
        val text = "$hex  LifeTrack-0.6.1-release.apk\n"

        assertEquals(hex, ApkFileValidator.parseSha256(text))
    }

    @Test
    fun parsesUppercaseDigest() {
        val hex = "03C037E470D5433ECBE6EF5E78040855ADB021E656702A20F64A358AC81C6AFF"

        assertEquals(hex.lowercase(), ApkFileValidator.parseSha256(hex))
    }

    @Test
    fun rejectsHtmlChecksumContent() {
        val error = assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.parseSha256("<!DOCTYPE html><html>login</html>")
        }
        assertTrue(error.message.orEmpty().contains("Invalid sha256"))
    }

    @Test
    fun rejectsTruncatedDigest() {
        assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.parseSha256("abc123  app.apk")
        }
    }

    @Test
    fun rejectsEmptyChecksumContent() {
        assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.parseSha256("   \n  ")
        }
    }
}
