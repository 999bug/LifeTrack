package com.example.lifetrack.core.update

import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFileValidatorTest {

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("lifetrack-apk-", ".tmp").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun acceptsZipMagicWithRealisticSize() {
        val bytes = ByteArray(1_000_001).apply {
            this[0] = 0x50
            this[1] = 0x4B
            this[2] = 0x03
            this[3] = 0x04
        }

        ApkFileValidator.validate(tempFile(bytes))
    }

    @Test
    fun rejectsHtmlEvenWhenBodyIsLarge() {
        val bytes = ByteArray(1_000_001).also {
            "<!DOCTYPE html><html>login</html>".toByteArray().copyInto(it)
        }

        val error = assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.validate(tempFile(bytes))
        }
        assertTrue(error.message.orEmpty().contains("ZIP magic"))
    }

    @Test
    fun rejectsTinyFileEvenWithZipMagic() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        val error = assertThrows(IllegalStateException::class.java) {
            ApkFileValidator.validate(tempFile(bytes))
        }
        assertTrue(error.message.orEmpty().contains("too small"))
    }
}
