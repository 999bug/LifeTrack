package com.example.lifetrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackTypeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(type: TrackTypeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(types: List<TrackTypeEntity>)

    @Query("SELECT * FROM track_type ORDER BY sortOrder ASC")
    suspend fun getAll(): List<TrackTypeEntity>

    /** 模块管理页需要响应启用/停用与排序的实时变化。 */
    @Query("SELECT * FROM track_type ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<TrackTypeEntity>>

    @Query("UPDATE track_type SET enabled = :enabled WHERE code = :code")
    suspend fun setEnabled(code: String, enabled: Boolean)

    @Update
    suspend fun updateAll(types: List<TrackTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(types: List<TrackTypeEntity>)

    @Query("DELETE FROM track_type")
    suspend fun deleteAll()
}
