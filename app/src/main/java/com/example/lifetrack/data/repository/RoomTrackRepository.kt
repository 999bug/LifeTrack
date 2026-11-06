package com.example.lifetrack.data.repository

import androidx.room.withTransaction
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.database.model.DailyCountProjection
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
