package com.example.lifetrack.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lifetrack.core.database.dao.TrackEventDao
import com.example.lifetrack.core.database.dao.TrackTypeDao
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity

@Database(
    entities = [TrackTypeEntity::class, TrackEventEntity::class, PoopDetailEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackTypeDao(): TrackTypeDao
    abstract fun trackEventDao(): TrackEventDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "life_track.db")
                .build()
    }
}
