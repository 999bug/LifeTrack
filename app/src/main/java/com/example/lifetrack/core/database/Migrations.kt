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

/**
 * v2 → v3：给 `track_event` 补 `localDate` 单列索引。
 *
 * 历史页「全部类型」、月历与统计的范围条件只带 `localDate`；复合索引
 * `(typeCode, localDate)` 在 `typeCode` 为 null 时前导列缺失，用不了 range 扫描，
 * 只能全表扫描并按 `occurredAtEpochMillis` 排序。只加索引，不动表结构与数据；
 * 索引名必须与 `3.json` 里的 `index_track_event_localDate` 一致。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_track_event_localDate` " +
                "ON `track_event` (`localDate`)"
        )
    }
}
