package com.example.lifetrack.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.lifetrack.core.database.entity.EventDetailEntity
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.EventWithDetails
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.database.model.StatDetailProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackEventDao {
    @Insert
    suspend fun insertEvent(event: TrackEventEntity)

    @Update
    suspend fun updateEvent(event: TrackEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPoopDetail(detail: PoopDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEventDetail(detail: EventDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEventDetails(details: List<EventDetailEntity>)

    @Query("DELETE FROM event_detail WHERE eventId = :eventId")
    suspend fun deleteEventDetail(eventId: String)

    @Query("DELETE FROM poop_detail WHERE eventId = :eventId")
    suspend fun deletePoopDetail(eventId: String)

    /** 今天页要显示所有启用类型的记录，不再按 typeCode 过滤。 */
    @Transaction
    @Query("SELECT * FROM track_event WHERE localDate = :localDate ORDER BY occurredAtEpochMillis DESC")
    fun observeRecordsForDate(localDate: String): Flow<List<EventWithDetails>>

    /** 备份快照：一条查询取全部事件与两种可选详情。 */
    @Transaction
    @Query("SELECT * FROM track_event ORDER BY occurredAtEpochMillis ASC")
    suspend fun getAllRecords(): List<EventWithDetails>

    @Query("SELECT * FROM track_event WHERE id = :eventId LIMIT 1")
    suspend fun findEvent(eventId: String): TrackEventEntity?

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

    @Transaction
    @Query("SELECT * FROM track_event WHERE typeCode = 'poop' ORDER BY occurredAtEpochMillis ASC")
    suspend fun getAllPoopEvents(): List<PoopRecordRelation>

    @Query("SELECT COUNT(*) FROM track_event")
    suspend fun countAllEvents(): Int

    @Query("DELETE FROM track_event")
    suspend fun deleteAllEvents()

    @Insert
    suspend fun insertEvents(events: List<TrackEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPoopDetails(details: List<PoopDetailEntity>)

    /** 统计时段分布只需要时刻与时区，不查 detail。 */
    @Query(
        "SELECT occurredAtEpochMillis AS occurredAtEpochMillis, zoneId AS zoneId FROM track_event " +
            "WHERE typeCode = 'poop' AND localDate BETWEEN :startDate AND :endDate"
    )
    fun observeEventTimestamps(
        startDate: String,
        endDate: String
    ): Flow<List<EventTimestamp>>

    /**
     * 形态分布：只取详情字段与本地日期，LEFT JOIN 让没有详情的快速打卡记录仍以 NULL 出现。
     * 不查备注与事件 ID，也不改 Room schema。
     */
    @Query(
        "SELECT e.localDate AS localDate, d.bristolType AS bristolType, d.amountCode AS amountCode, " +
            "d.effortCode AS effortCode, d.urgency AS urgency, " +
            "d.abdominalDiscomfort AS abdominalDiscomfort, " +
            "d.incompleteEvacuation AS incompleteEvacuation " +
            "FROM track_event e LEFT JOIN poop_detail d ON d.eventId = e.id " +
            "WHERE e.typeCode = 'poop' AND e.localDate BETWEEN :startDate AND :endDate"
    )
    fun observeStatDetails(
        startDate: String,
        endDate: String
    ): Flow<List<StatDetailProjection>>
}
