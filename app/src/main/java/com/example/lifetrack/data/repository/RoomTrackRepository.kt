package com.example.lifetrack.data.repository

import androidx.room.withTransaction
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.RecordTimeMapper
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomTrackRepository(
    private val database: AppDatabase
) : TrackRepository {
    private val dao = database.trackEventDao()

    override fun observePoopRecordsForDate(localDate: String): Flow<List<PoopRecordRelation>> =
        dao.observePoopRecordsForDate(localDate)

    override fun observePoopRecordsInRange(
        startDate: String,
        endDate: String
    ): Flow<List<PoopRecordRelation>> = dao.observePoopRecordsInRange(startDate, endDate)

    override fun observeDailyCounts(
        startDate: String,
        endDate: String
    ): Flow<List<DailyCountProjection>> = dao.observeDailyCounts(startDate, endDate)

    override fun observeEventTimestamps(
        startDate: String,
        endDate: String
    ): Flow<List<EventTimestamp>> = dao.observeEventTimestamps(startDate, endDate)

    override suspend fun addPoopRecord(draft: PoopRecordDraft): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val time = RecordTimeMapper.from(draft.occurredAtEpochMillis, draft.zoneId)
        database.withTransaction {
            database.trackTypeDao().insertIgnore(
                TrackTypeEntity("poop", "排便", "poop", "body", "EVENT", true, 10, 1)
            )
            dao.insertEvent(
                TrackEventEntity(
                    id = id,
                    typeCode = "poop",
                    occurredAtEpochMillis = time.occurredAtEpochMillis,
                    localDate = time.localDate,
                    zoneId = time.zoneId,
                    note = draft.note,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
            dao.putPoopDetail(draft.toDetail(id))
        }
        return id
    }

    override suspend fun updatePoopRecord(eventId: String, draft: PoopRecordDraft) {
        val existing = dao.findPoopEvent(eventId) ?: return
        val now = System.currentTimeMillis()
        val time = RecordTimeMapper.from(draft.occurredAtEpochMillis, draft.zoneId)
        database.withTransaction {
            dao.updateEvent(
                existing.copy(
                    occurredAtEpochMillis = time.occurredAtEpochMillis,
                    localDate = time.localDate,
                    zoneId = time.zoneId,
                    note = draft.note,
                    updatedAtEpochMillis = now
                )
            )
            dao.putPoopDetail(draft.toDetail(eventId))
        }
    }

    override suspend fun deletePoopRecord(eventId: String) {
        dao.deleteEvent(eventId)
    }

    override suspend fun readBackup(): BackupPayload = BackupPayload(
        types = database.trackTypeDao().getAll(),
        events = dao.getAllPoopEvents()
    )

    override suspend fun restoreBackup(payload: BackupPayload) {
        database.withTransaction {
            // 先删事件（详情随外键级联），再重建类型，顺序反了会触发外键约束
            dao.deleteAllEvents()
            database.trackTypeDao().deleteAll()
            val types = payload.types.toMutableList()
            if (types.none { it.code == DEFAULT_POOP_TYPE.code }) types += DEFAULT_POOP_TYPE
            database.trackTypeDao().insertAll(types)
            dao.insertEvents(payload.events.map { it.event })
            dao.putPoopDetails(payload.events.mapNotNull { it.detail })
        }
    }

    override suspend fun countAllRecords(): Int = dao.countAllEvents()

    private fun PoopRecordDraft.toDetail(eventId: String) = PoopDetailEntity(
        eventId = eventId,
        bristolType = bristolType,
        amountCode = amountCode,
        effortCode = effortCode,
        urgency = urgency,
        abdominalDiscomfort = abdominalDiscomfort,
        incompleteEvacuation = incompleteEvacuation
    )
}
