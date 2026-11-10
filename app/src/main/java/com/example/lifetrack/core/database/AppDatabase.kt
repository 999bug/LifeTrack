package com.example.lifetrack.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lifetrack.core.database.dao.TrackEventDao
import com.example.lifetrack.core.database.dao.TrackTypeDao
import com.example.lifetrack.core.database.entity.EventDetailEntity
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TrackTypeEntity::class,
        TrackEventEntity::class,
        PoopDetailEntity::class,
        EventDetailEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackTypeDao(): TrackTypeDao
    abstract fun trackEventDao(): TrackEventDao

    companion object {
        const val NAME = "life_track.db"

        /** 0.6.0 起：用 Keystore 解出的口令打开加密库。迁移必须在调用本方法之前完成。 */
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        /**
         * 加密链路异常时的明文兜底：保证 0.5.0 存量用户仍能打开 App、不丢数据。
         * 只覆盖「迁移失败、原文件已还原为明文」的场景，不代表加密目标已达成。
         */
        fun createPlaintextFallback(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
