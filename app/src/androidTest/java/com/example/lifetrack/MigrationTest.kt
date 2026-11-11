package com.example.lifetrack

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.MIGRATION_1_2
import com.example.lifetrack.core.database.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B1：Room 1 → 2 的真实迁移用例。
 *
 * 用 `app/schemas/` 里的 1.json 建一个 v1 库（该 schema 由 KSP 导出，与已发布的 v0.1.0 同构），
 * 写入类型、事件与排便详情，再按生产配置跑到 v2 并校验：
 * 1. 迁移 SQL 与 2.json 完全一致（[MigrationTestHelper.runMigrationsAndValidate] 会比对 identityHash）；
 * 2. 存量数据一行不少、字段值不变；
 * 3. 新增的 event_detail 表可用，外键级联删除生效。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_keepsExistingRecords() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO track_type (code, name, iconKey, category, trackingMode, enabled, sortOrder, schemaVersion) " +
                    "VALUES ('poop', '排便', 'poop', 'body', 'EVENT', 1, 10, 1)"
            )
            db.execSQL(
                "INSERT INTO track_event (id, typeCode, occurredAtEpochMillis, localDate, zoneId, note, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES ('e1', 'poop', 1762657200000, '2026-11-09', 'Asia/Tokyo', '迁移前备注', " +
                    "1762657200000, 1762657200000)"
            )
            db.execSQL(
                "INSERT INTO poop_detail (eventId, bristolType, amountCode, effortCode, urgency, " +
                    "abdominalDiscomfort, incompleteEvacuation) VALUES ('e1', 4, 2, 1, 0, 1, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT typeCode, localDate, zoneId, note FROM track_event WHERE id = 'e1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("poop", cursor.getString(0))
            assertEquals("2026-11-09", cursor.getString(1))
            assertEquals("Asia/Tokyo", cursor.getString(2))
            assertEquals("迁移前备注", cursor.getString(3))
        }
        db.query("SELECT bristolType, amountCode FROM poop_detail WHERE eventId = 'e1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(4, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM track_type").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("PRAGMA foreign_key_list(event_detail)").use { cursor ->
            assertTrue("event_detail 应带外键", cursor.count > 0)
        }
    }

    @Test
    fun migrate1To2_acceptsGenericDetailAndCascadesDelete() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO track_event (id, typeCode, occurredAtEpochMillis, localDate, zoneId, note, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES ('e2', 'water', 1762657200000, '2026-11-09', 'Asia/Tokyo', '', " +
                    "1762657200000, 1762657200000)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO event_detail (eventId, typeCode, payload) VALUES ('e2', 'water', '{\"amountMl\":\"250\"}')"
        )
        db.query("SELECT payload FROM event_detail WHERE eventId = 'e2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"amountMl\":\"250\"}", cursor.getString(0))
        }

        db.execSQL("DELETE FROM track_event WHERE id = 'e2'")
        db.query("SELECT COUNT(*) FROM event_detail WHERE eventId = 'e2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("删除事件后详情应随外键级联删除", 0, cursor.getInt(0))
        }
    }

    /**
     * v2 → v3：只补一条 `localDate` 单列索引。
     *
     * `runMigrationsAndValidate` 会把迁移后的表结构（含索引名与列）与 `3.json` 的
     * identityHash 比对，索引名写错、漏建或多建都会在这里失败。
     */
    @Test
    fun migrate2To3_addsLocalDateIndexAndKeepsRecords() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO track_type (code, name, iconKey, category, trackingMode, enabled, sortOrder, schemaVersion) " +
                    "VALUES ('poop', '排便', 'poop', 'body', 'EVENT', 1, 10, 1)"
            )
            db.execSQL(
                "INSERT INTO track_event (id, typeCode, occurredAtEpochMillis, localDate, zoneId, note, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES ('e3', 'poop', 1762657200000, '2026-11-10', 'Asia/Tokyo', '索引迁移前', " +
                    "1762657200000, 1762657200000)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query("SELECT localDate, note FROM track_event WHERE id = 'e3'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-11-10", cursor.getString(0))
            assertEquals("索引迁移前", cursor.getString(1))
        }
        db.query("PRAGMA index_list(track_event)").use { cursor ->
            val names = buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertTrue(
                "应新增 localDate 单列索引，实际：$names",
                names.contains("index_track_event_localDate")
            )
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
