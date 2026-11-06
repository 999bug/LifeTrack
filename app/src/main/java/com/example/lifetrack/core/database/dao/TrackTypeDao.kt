package com.example.lifetrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.example.lifetrack.core.database.entity.TrackTypeEntity

@Dao
interface TrackTypeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(type: TrackTypeEntity)
}
