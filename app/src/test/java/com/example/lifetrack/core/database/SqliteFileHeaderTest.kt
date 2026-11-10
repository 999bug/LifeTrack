package com.example.lifetrack.core.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SqliteFileHeaderTest {

    private val realHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Test
    fun acceptsRealSqliteHeader() {
        assertTrue(SqliteFileHeader.isPlaintext(realHeader))
        assertTrue(SqliteFileHeader.isPlaintext(realHeader + ByteArray(64)))
    }

    @Test
    fun rejectsEncryptedOrGarbageHeader() {
        assertFalse(SqliteFileHeader.isPlaintext(ByteArray(16) { 0x7f }))
        assertFalse(SqliteFileHeader.isPlaintext("SQLite format 3 ".toByteArray()))
        assertFalse(SqliteFileHeader.isPlaintext(realHeader.copyOf().also { it[7] = 'X'.code.toByte() }))
    }

    @Test
    fun rejectsTruncatedOrEmptyHeader() {
        assertFalse(SqliteFileHeader.isPlaintext(ByteArray(0)))
        assertFalse(SqliteFileHeader.isPlaintext(realHeader.copyOf(15)))
    }

    @Test
    fun fileVariant_readsOnlyTheHeader() {
        val file = File.createTempFile("lifetrack-header", ".db")
        try {
            assertFalse(SqliteFileHeader.isPlaintext(file))
            file.writeBytes(realHeader + ByteArray(128) { 9 })
            assertTrue(SqliteFileHeader.isPlaintext(file))
            file.writeBytes(ByteArray(128) { 3 })
            assertFalse(SqliteFileHeader.isPlaintext(file))
        } finally {
            file.delete()
        }
    }
}
