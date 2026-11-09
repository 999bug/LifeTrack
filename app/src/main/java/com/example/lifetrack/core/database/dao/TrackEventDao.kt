package com.example.lifetrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.PoopRecordRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackEventDao {
    @Insert
    suspend fun insertEvent(event: TrackEventEntity)

    @Update
    suspend fun updateEvent(event: TrackEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPoopDetail(detail: PoopDetailEntity)

    @Query("SELECT * FROM track_event WHERE id = :eventId AND typeCode = 'poop' LIMIT 1")
    suspend fun findPoopEvent(eventId: String): TrackEventEntity?

    @Query("DELETE FROM track_event WHERE id = :eventId")
    suspend fun deleteEvent(eventId: String)

    @Transaction
    @Query(
        "SELECT * FROM track_event " +
            "WHERE typeCode = 'poop' AND localDate = :localDate " +
            "ORDER BY occurredAtEpochMillis DESC"
    )
    fun observePoopRecordsForDate(localDate: String): Flow<List<PoopRecordRelation>>

    @Transaction
    @Query(
        "SELECT * FROM track_event " +
            "WHERE typeCode = 'poop' AND localDate BETWEEN :startDate AND :endDate " +
            "ORDER BY occurredAtEpochMillis DESC"
    )
    fun observePoopRecordsInRange(
        startDate: String,
        endDate: String
    ): Flow<List<PoopRecordRelation>>

    @Query(
        "SELECT localDate AS localDate, COUNT(*) AS count FROM track_event " +
            "WHERE typeCode = 'poop' AND localDate BETWEEN :startDate AND :endDate " +
            "GROUP BY localDate ORDER BY localDate ASC"
    )
    fun observeDailyCounts(startDate: String, endDate: String): Flow<List<DailyCountProjection>>

    /** 统计时段分布只需要时刻与时区，不查 detail。 */
    @Query(
        "SELECT occurredAtEpochMillis AS occurredAtEpochMillis, zoneId AS zoneId FROM track_event " +
            "WHERE typeCode = 'poop' AND localDate BETWEEN :startDate AND :endDate"
    )
    fun observeEventTimestamps(
        startDate: String,
        endDate: String
    ): Flow<List<EventTimestamp>>
}
