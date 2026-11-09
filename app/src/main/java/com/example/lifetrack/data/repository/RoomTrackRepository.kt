package com.example.lifetrack.data.repository

import androidx.room.withTransaction
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.EventDetailEntity
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.EventWithDetails
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.database.model.StatDetailProjection
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.RecordDetailCodec
import com.example.lifetrack.core.model.RecordTimeMapper
import com.example.lifetrack.core.model.toEntity
import com.example.lifetrack.core.model.toRecordDetail
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomTrackRepository(
    private val database: AppDatabase
) : TrackRepository {
    private val dao = database.trackEventDao()

    override fun observePoopRecordsForDate(localDate: String): Flow<List<PoopRecordRelation>> =
        dao.observePoopRecordsForDate(localDate)

    override fun observeRecordsForDate(localDate: String): Flow<List<BackupRecord>> =
        dao.observeRecordsForDate(localDate).map { rows -> rows.map { it.toBackupRecord() } }

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

    override fun observeStatDetails(
        startDate: String,
        endDate: String
    ): Flow<List<StatDetailProjection>> = dao.observeStatDetails(startDate, endDate)

    override fun observeTrackTypes(): Flow<List<TrackTypeEntity>> =
        database.trackTypeDao().observeAll()

    override suspend fun ensureDefaultTypes() {
        database.trackTypeDao().insertIgnoreAll(DEFAULT_TYPES)
    }

    override suspend fun setTypeEnabled(code: String, enabled: Boolean) {
        database.trackTypeDao().setEnabled(code, enabled)
    }

    override suspend fun reorderTypes(types: List<TrackTypeEntity>) {
        database.withTransaction { database.trackTypeDao().updateAll(types) }
    }

    override suspend fun addPoopRecord(draft: PoopRecordDraft): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val time = RecordTimeMapper.from(draft.occurredAtEpochMillis, draft.zoneId)
        database.withTransaction {
            database.trackTypeDao().insertIgnore(DEFAULT_POOP_TYPE)
            dao.insertEvent(
                TrackEventEntity(
                    id = id,
                    typeCode = DEFAULT_POOP_TYPE.code,
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

    override suspend fun addRecord(draft: RecordDraft): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val time = RecordTimeMapper.from(draft.occurredAtEpochMillis, draft.zoneId)
        database.withTransaction {
            database.trackTypeDao().insertIgnore(typeFor(draft.typeCode))
            dao.insertEvent(
                TrackEventEntity(
                    id = id,
                    typeCode = draft.typeCode,
                    occurredAtEpochMillis = time.occurredAtEpochMillis,
                    localDate = time.localDate,
                    zoneId = time.zoneId,
                    note = draft.note,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
            writeDetail(id, draft.detail)
        }
        return id
    }

    override suspend fun updateRecord(eventId: String, draft: RecordDraft) {
        val existing = dao.findEvent(eventId) ?: return
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
            // 编辑可能把详情从「有」改成「无」或换类型，先清干净再按草稿写
            dao.deletePoopDetail(eventId)
            dao.deleteEventDetail(eventId)
            writeDetail(eventId, draft.detail)
        }
    }

    override suspend fun readBackup(): BackupPayload = BackupPayload(
        types = database.trackTypeDao().getAll(),
        events = dao.getAllRecords().map { it.toBackupRecord() }
    )

    override suspend fun restoreBackup(payload: BackupPayload) {
        val poopDetails = ArrayList<PoopDetailEntity>()
        val genericDetails = ArrayList<EventDetailEntity>()
        payload.events.forEach { record ->
            when (val detail = record.detail) {
                null -> Unit
                is RecordDetail.Poop -> poopDetails += detail.toEntity(record.event.id)
                is RecordDetail.Generic -> {
                    require(detail.typeCode == record.event.typeCode) {
                        "详情类型与事件类型不一致：${detail.typeCode}/${record.event.typeCode}"
                    }
                    genericDetails += EventDetailEntity(
                        eventId = record.event.id,
                        typeCode = detail.typeCode,
                        payload = RecordDetailCodec.encode(detail.fields)
                    )
                }
            }
        }
        database.withTransaction {
            // 先删事件（两类详情随外键级联），再重建类型，顺序反了会触发外键约束
            dao.deleteAllEvents()
            database.trackTypeDao().deleteAll()
            val types = payload.types.toMutableList()
            DEFAULT_TYPES.forEach { default ->
                if (types.none { it.code == default.code }) types += default
            }
            database.trackTypeDao().insertAll(types)
            dao.insertEvents(payload.events.map { it.event })
            dao.putPoopDetails(poopDetails)
            dao.putEventDetails(genericDetails)
        }
    }

    override suspend fun countAllRecords(): Int = dao.countAllEvents()

    private suspend fun writeDetail(eventId: String, detail: RecordDetail?) {
        when (detail) {
            null -> Unit
            is RecordDetail.Poop -> dao.putPoopDetail(detail.toEntity(eventId))
            is RecordDetail.Generic -> dao.putEventDetail(
                EventDetailEntity(
                    eventId = eventId,
                    typeCode = detail.typeCode,
                    payload = RecordDetailCodec.encode(detail.fields)
                )
            )
        }
    }

    private fun typeFor(code: String): TrackTypeEntity =
        DEFAULT_TYPES.firstOrNull { it.code == code }
            ?: TrackTypeEntity(code, code, code, "other", "EVENT", true, 999, 1)

    private fun EventWithDetails.toBackupRecord(): BackupRecord {
        val detail: RecordDetail? = when {
            event.typeCode == DEFAULT_POOP_TYPE.code -> poop?.toRecordDetail()
            generic != null -> RecordDetailCodec.decode(generic.payload)?.let {
                RecordDetail.Generic(generic.typeCode, it)
            }
            else -> null
        }
        return BackupRecord(event = event, detail = detail)
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
