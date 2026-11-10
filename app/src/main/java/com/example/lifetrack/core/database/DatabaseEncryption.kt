package com.example.lifetrack.core.database

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * 把 0.5.0 及更早版本留下的明文 Room 库原地换成 SQLCipher 加密库，schema 与 `user_version` 保持不变。
 *
 * 兼容性口径见 `docs/security-hardening-confirmation.md` §2.6：
 * - `sqlcipher_export` 不复制 `user_version`，必须原样写回，否则从 Room v1 跳过来的用户会丢掉 `MIGRATION_1_2`；
 * - 迁移在 Room 打开之前完成；明文备份保留到加密库首次成功读取之后才删；
 * - 明文 `-wal` / `-shm` / `-journal` 必须在替换后清掉，否则会套到加密库上；
 * - 任何一步失败都还原明文并抛错，**绝不静默丢数据**，也不留半加密状态。
 */
class DatabaseEncryption(private val context: Context) {

    sealed interface Outcome {
        val needsConfirmation: Boolean

        /** 全新安装，Room 会直接创建加密库。 */
        object Fresh : Outcome {
            override val needsConfirmation = false
        }

        /** 已是加密库；[needsConfirmation] 表示上次迁移遗留的明文备份还没清理。 */
        data class AlreadyEncrypted(override val needsConfirmation: Boolean) : Outcome

        data class Migrated(
            val originalVersion: Int,
            val rowCounts: Map<String, Long>,
            /** true 表示这次是从中断状态恢复后重做的。 */
            val recovered: Boolean = false
        ) : Outcome {
            override val needsConfirmation = true
        }
    }

    class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val BACKUP_SUFFIX = ".plaintext.bak"
        private const val TEMP_SUFFIX = ".encrypted.tmp"
        private const val STALE_SUFFIX = ".swap"
        private const val TABLE_ABSENT = -1L

        /** 项目实际的四张表。v1 库没有 `event_detail`，缺表记 -1，两侧一致即可。 */
        private val TABLES = listOf("track_type", "track_event", "poop_detail", "event_detail")
    }

    fun migrateIfNeeded(dbName: String, passphrase: ByteArray): Outcome {
        System.loadLibrary("sqlcipher")
        val dbFile = context.getDatabasePath(dbName)
        val backup = suffix(dbFile, BACKUP_SUFFIX)
        val temp = suffix(dbFile, TEMP_SUFFIX)
        val stale = suffix(dbFile, STALE_SUFFIX)

        if (!dbFile.exists()) {
            // 替换阶段被中断：原路径空了。先从完整明文备份还原再重做，避免停在半加密状态。
            if (backup.exists()) {
                restore(dbFile, backup)
                temp.delete()
                stale.delete()
                val (version, counts) = encrypt(dbFile, backup, temp, stale, passphrase)
                return Outcome.Migrated(version, counts, recovered = true)
            }
            temp.delete()
            stale.delete()
            return Outcome.Fresh
        }

        if (!SqliteFileHeader.isPlaintext(dbFile)) {
            return Outcome.AlreadyEncrypted(backup.exists())
        }

        val (version, counts) = encrypt(dbFile, backup, temp, stale, passphrase)
        return Outcome.Migrated(version, counts)
    }

    /** 加密库已被成功读取后调用：清掉明文备份，避免明文数据长期留在设备上。 */
    fun confirmMigration(dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        listOf(
            suffix(dbFile, BACKUP_SUFFIX),
            suffix(dbFile, "$BACKUP_SUFFIX-wal"),
            suffix(dbFile, "$BACKUP_SUFFIX-shm"),
            suffix(dbFile, TEMP_SUFFIX),
            suffix(dbFile, STALE_SUFFIX)
        ).forEach { it.delete() }
    }

    private fun encrypt(
        dbFile: File,
        backup: File,
        temp: File,
        stale: File,
        passphrase: ByteArray
    ): Pair<Int, Map<String, Long>> {
        temp.delete()
        stale.delete()

        val plain = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            throw MigrationException("cannot open plaintext database", e)
        }

        val version: Int
        val counts: Map<String, Long>
        try {
            // WAL 里可能还有没落盘的写入；先 checkpoint，保证备份与导出都不漏数据。
            try {
                plain.rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            } catch (_: Exception) {
                // 非 WAL 模式的库不支持该 pragma，忽略即可。
            }
            version = plain.version
            counts = readCounts(plain)
        } finally {
            plain.close()
        }

        copyPlainFiles(dbFile, backup)

        var encrypted: SQLiteDatabase? = null
        var replacementStarted = false
        try {
            encrypted = SQLiteDatabase.openOrCreateDatabase(temp, passphrase, null, null)
            encrypted.rawExecSQL(
                "ATTACH DATABASE '${escape(dbFile.absolutePath)}' AS plaintext KEY ''"
            )
            // 双参数形式：第一个是目标，第二个是源 —— 从明文 plaintext 复制到加密 main。
            encrypted.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext')")
            // 官方明确 sqlcipher_export 不会复制 user_version，必须自己写回，Room 靠它判断 schema 版本。
            encrypted.version = version
            encrypted.rawExecSQL("DETACH DATABASE plaintext")

            val exportedCounts = readCounts(encrypted)
            if (encrypted.version != version || exportedCounts != counts) {
                throw MigrationException(
                    "verification failed: version ${encrypted.version}/$version, rows $exportedCounts/$counts"
                )
            }
            encrypted.close()
            encrypted = null

            replacementStarted = true
            if (!dbFile.renameTo(stale)) {
                throw MigrationException("cannot stage plaintext database for replacement")
            }
            if (!temp.renameTo(dbFile)) {
                stale.renameTo(dbFile)
                replacementStarted = false
                throw MigrationException("cannot install encrypted database")
            }
            // 明文的 WAL/SHM/JOURNAL 绝不能留给加密库。
            listOf("-wal", "-shm", "-journal").forEach { suffix(dbFile, it).delete() }
            stale.delete()
            replacementStarted = false
        } catch (e: Exception) {
            encrypted?.close()
            temp.delete()
            // 只有替换阶段出过问题才需要还原明文；导出阶段失败时原文件根本没动过。
            if (replacementStarted && !SqliteFileHeader.isPlaintext(dbFile)) {
                restore(dbFile, backup)
            }
            if (e is MigrationException) throw e
            throw MigrationException("encryption export failed", e)
        }

        return version to counts
    }

    private fun readCounts(db: SQLiteDatabase): Map<String, Long> = TABLES.associateWith { table ->
        try {
            val cursor = db.query("SELECT COUNT(*) FROM $table")
            try {
                if (cursor.moveToFirst()) cursor.getLong(0) else TABLE_ABSENT
            } finally {
                cursor.close()
            }
        } catch (_: Exception) {
            // Room v1 没有 event_detail；缺表不是错误，两侧都记 -1 即可通过比对。
            TABLE_ABSENT
        }
    }

    private fun copyPlainFiles(dbFile: File, backup: File) {
        backup.delete()
        dbFile.copyTo(backup, overwrite = true)
        listOf("-wal", "-shm").forEach { ext ->
            val source = suffix(dbFile, ext)
            val target = suffix(backup, ext)
            target.delete()
            if (source.exists()) source.copyTo(target, overwrite = true)
        }
    }

    private fun restore(dbFile: File, backup: File) {
        if (!backup.exists()) throw MigrationException("plaintext backup missing, cannot restore")
        if (dbFile.exists()) dbFile.delete()
        backup.copyTo(dbFile, overwrite = true)
        listOf("-wal", "-shm").forEach { ext ->
            val source = suffix(backup, ext)
            val target = suffix(dbFile, ext)
            target.delete()
            if (source.exists()) source.copyTo(target, overwrite = true)
        }
    }

    private fun suffix(file: File, value: String): File = File(file.path + value)

    private fun escape(path: String): String = path.replace("'", "''")
}
