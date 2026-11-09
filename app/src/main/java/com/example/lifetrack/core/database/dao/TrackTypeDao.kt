package com.example.lifetrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lifetrack.core.database.entity.TrackTypeEntity

@Dao
interface TrackTypeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(type: TrackTypeEntity)

    @Query("SELECT * FROM track_type ORDER BY sortOrder ASC")
    suspend fun getAll(): List<TrackTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(types: List<TrackTypeEntity>)

    @Query("DELETE FROM track_type")
    suspend fun deleteAll()
}
