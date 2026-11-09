package com.example.lifetrack.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：只新增通用详情表，不改写任何存量数据。
 *
 * SQL 必须与 `app/schemas/.../2.json` 的 createSql / index 一致，否则 Room 打开时
 * 会抛「Migration didn't properly handle」。已有 v1 库的表结构仍是 v2 的子集，
 * 因此升级本身不丢数据；但 Room 不允许版本号降级，回退只能卸载重装。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_detail` " +
                "(`eventId` TEXT NOT NULL, `typeCode` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                "PRIMARY KEY(`eventId`), " +
                "FOREIGN KEY(`eventId`) REFERENCES `track_event`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_event_detail_typeCode` " +
                "ON `event_detail` (`typeCode`)"
        )
    }
}
