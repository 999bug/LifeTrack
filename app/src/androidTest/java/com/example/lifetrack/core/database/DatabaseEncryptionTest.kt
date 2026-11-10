package com.example.lifetrack.core.database

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 在真实设备上验证 0.5.0 明文库 → 0.6.0 加密库的搬迁、兼容性与中断恢复。 */
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "encryption_migration_test.db"
    private val passphrase = ByteArray(32) { (it * 5 + 1).toByte() }
    private lateinit var encryption: DatabaseEncryption

    @Before
    fun setUp() {
        cleanup()
        encryption = DatabaseEncryption(context)
    }

    @After
    fun tearDown() {
        cleanup()
    }

    /** 0.4.0 / 0.5.0 的 Room v2 明文库：升级后版本与全部记录都必须保真。 */
    @Test
    fun migratesPlaintextV2_keepsVersionRowsAndData() {
        createPlainDatabase(version = 2, withEventDetail = true)

        val outcome = encryption.migrateIfNeeded(dbName, passphrase)
        assertTrue(outcome is DatabaseEncryption.Outcome.Migrated)
        val migrated = outcome as DatabaseEncryption.Outcome.Migrated
        assertEquals(2, migrated.originalVersion)
        assertEquals(2L, migrated.rowCounts["track_event"])

        val file = context.getDatabasePath(dbName)
        assertFalse(SqliteFileHeader.isPlaintext(file))
        assertTrue(File(file.path + ".plaintext.bak").exists())

        openEncrypted(file).use { db ->
            assertEquals(2, db.version)
            assertEquals(2L, queryLong(db, "SELECT COUNT(*) FROM track_event"))
            assertEquals(1L, queryLong(db, "SELECT COUNT(*) FROM event_detail"))
            assertEquals("keep-me", queryString(db, "SELECT note FROM track_event WHERE id = 'e1'"))
        }

        encryption.confirmMigration(dbName)
        assertFalse(File(file.path + ".plaintext.bak").exists())

        // 重复调用不能再次搬迁，也不能破坏已有数据。
        assertTrue(encryption.migrateIfNeeded(dbName, passphrase) is DatabaseEncryption.Outcome.AlreadyEncrypted)
        openEncrypted(file).use { db ->
            assertEquals(2L, queryLong(db, "SELECT COUNT(*) FROM track_event"))
        }
    }

    /** 兼容性：0.1.0–0.3.0 是 Room v1（无 event_detail），不能把它误写成 v2。 */
    @Test
    fun migratesPlaintextV1_preservesUserVersionOne() {
        createPlainDatabase(version = 1, withEventDetail = false)

        val outcome = encryption.migrateIfNeeded(dbName, passphrase)
        assertTrue(outcome is DatabaseEncryption.Outcome.Migrated)
        assertEquals(1, (outcome as DatabaseEncryption.Outcome.Migrated).originalVersion)
        assertEquals(-1L, outcome.rowCounts["event_detail"])

        openEncrypted(context.getDatabasePath(dbName)).use { db ->
            assertEquals(1, db.version)
            assertEquals(2L, queryLong(db, "SELECT COUNT(*) FROM track_event"))
        }
    }

    /** 迁移在替换阶段被强杀：下次启动从明文备份自动恢复并重做。 */
    @Test
    fun recoversFromInterruptedReplacement() {
        createPlainDatabase(version = 2, withEventDetail = true)
        val file = context.getDatabasePath(dbName)
        file.copyTo(File(file.path + ".plaintext.bak"), overwrite = true)
        file.delete()

        val outcome = encryption.migrateIfNeeded(dbName, passphrase)
        assertTrue(outcome is DatabaseEncryption.Outcome.Migrated)
        assertTrue((outcome as DatabaseEncryption.Outcome.Migrated).recovered)

        val restored = context.getDatabasePath(dbName)
        assertFalse(SqliteFileHeader.isPlaintext(restored))
        openEncrypted(restored).use { db ->
            assertEquals(2, db.version)
            assertEquals(2L, queryLong(db, "SELECT COUNT(*) FROM track_event"))
        }
    }

    /** 加密后的库用错误口令必须打不开，证明确实加密了而不是空转。 */
    @Test
    fun wrongPassphraseCannotOpenEncryptedDatabase() {
        createPlainDatabase(version = 2, withEventDetail = true)
        encryption.migrateIfNeeded(dbName, passphrase)

        val wrong = ByteArray(32) { 99 }
        val file = context.getDatabasePath(dbName)
        var rejected = false
        try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath, wrong, null, SQLiteDatabase.OPEN_READWRITE, null
            )
            db.close()
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private fun createPlainDatabase(version: Int, withEventDetail: Boolean) {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("CREATE TABLE track_type (code TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE track_event (id TEXT PRIMARY KEY, note TEXT)")
            db.execSQL("CREATE TABLE poop_detail (eventId TEXT PRIMARY KEY)")
            if (withEventDetail) {
                db.execSQL("CREATE TABLE event_detail (eventId TEXT PRIMARY KEY, payload TEXT)")
                db.execSQL("INSERT INTO event_detail VALUES ('e1', 'water')")
            }
            db.execSQL("INSERT INTO track_type VALUES ('poop')")
            db.execSQL("INSERT INTO track_event VALUES ('e1', 'keep-me')")
            db.execSQL("INSERT INTO track_event VALUES ('e2', 'second')")
            db.execSQL("PRAGMA user_version = $version")
        } finally {
            db.close()
        }
    }

    private fun openEncrypted(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null
        )

    private fun queryLong(db: SQLiteDatabase, sql: String): Long =
        query(db, sql) { it.getLong(0) }

    private fun queryString(db: SQLiteDatabase, sql: String): String =
        query(db, sql) { it.getString(0) }

    private fun <T> query(db: SQLiteDatabase, sql: String, read: (android.database.Cursor) -> T): T {
        val cursor = db.query(sql)
        return try {
            check(cursor.moveToFirst()) { "query returned no rows: $sql" }
            read(cursor)
        } finally {
            cursor.close()
        }
    }

    private fun cleanup() {
        val file = context.getDatabasePath(dbName)
        listOf(
            "", "-wal", "-shm", "-journal",
            ".plaintext.bak", ".plaintext.bak-wal", ".plaintext.bak-shm",
            ".encrypted.tmp", ".swap"
        ).forEach { File(file.path + it).delete() }
    }
}
