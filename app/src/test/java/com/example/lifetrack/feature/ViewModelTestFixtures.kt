package com.example.lifetrack.feature

import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.domain.model.DailyCount
import com.example.lifetrack.domain.model.DailyTypeCount
import com.example.lifetrack.domain.model.EventTimestamp
import com.example.lifetrack.domain.model.HistoryPage
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordCursor
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.model.StatDetail
import com.example.lifetrack.domain.model.TrackType
import com.example.lifetrack.domain.repository.RecordRepository
import com.example.lifetrack.domain.repository.StatisticsRepository
import com.example.lifetrack.domain.repository.TypeCatalogRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal fun record(
    id: String,
    date: LocalDate = LocalDate.of(2026, 11, 19),
    occurredAt: Long = 1_000L,
    typeCode: String = "poop"
): BackupRecord = BackupRecord(
    event = TrackEventEntity(
        id = id,
        typeCode = typeCode,
        occurredAtEpochMillis = occurredAt,
        localDate = date.toString(),
        zoneId = ZoneId.of("Asia/Shanghai").id,
        note = "",
        createdAtEpochMillis = occurredAt,
        updatedAtEpochMillis = occurredAt
    )
)

internal class FakeRecordRepository : RecordRepository {
    val recordsForDate = MutableStateFlow<List<BackupRecord>>(emptyList())
    val recordsInRange = MutableStateFlow<List<BackupRecord>>(emptyList())
    val requestedDates = mutableListOf<String>()
    var pageFactory: (RecordCursor?) -> HistoryPage = { HistoryPage(emptyList(), null, false) }
    var addFailure: Throwable? = null
    var addedRecords = 0
    var deletedRecords = 0

    override fun observeRecordsForDate(localDate: String): Flow<List<BackupRecord>> {
        requestedDates += localDate
        return recordsForDate
    }

    override fun observeRecordsInRange(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<BackupRecord>> = recordsInRange

    override fun observeHistoryPage(
        startDate: String,
        endDate: String,
        typeCode: String?,
        cursor: RecordCursor?,
        limit: Int
    ): Flow<HistoryPage> = flowOf(pageFactory(cursor))

    override suspend fun addPoopRecord(draft: PoopRecordDraft): String {
        addFailure?.let { throw it }
        addedRecords += 1
        return "new-poop"
    }

    override suspend fun updatePoopRecord(eventId: String, draft: PoopRecordDraft) = Unit

    override suspend fun deletePoopRecord(eventId: String) {
        deletedRecords += 1
    }

    override suspend fun addRecord(draft: RecordDraft): String {
        addFailure?.let { throw it }
        addedRecords += 1
        return "new-record"
    }

    override suspend fun updateRecord(eventId: String, draft: RecordDraft) = Unit
}

internal class FakeStatisticsRepository : StatisticsRepository {
    val dailyCounts = MutableStateFlow<List<DailyCount>>(emptyList())
    val dailyTypeCounts = MutableStateFlow<List<DailyTypeCount>>(emptyList())
    val timestamps = MutableStateFlow<List<EventTimestamp>>(emptyList())
    val details = MutableStateFlow<List<StatDetail>>(emptyList())

    override fun observeDailyCounts(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<DailyCount>> = dailyCounts

    override fun observeDailyTypeCounts(
        startDate: String,
        endDate: String
    ): Flow<List<DailyTypeCount>> = dailyTypeCounts

    override fun observeEventTimestamps(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<EventTimestamp>> = timestamps

    override fun observeStatDetails(
        startDate: String,
        endDate: String
    ): Flow<List<StatDetail>> = details
}

internal class FakeTypeCatalogRepository : TypeCatalogRepository {
    val types = MutableStateFlow(
        listOf(
            TrackType("poop", "排便", "poop", "body", "EVENT", true, 10, 1),
            TrackType("water", "饮水", "water", "drink", "EVENT", true, 20, 1)
        )
    )

    override fun observeTrackTypes(): Flow<List<TrackType>> = types

    override suspend fun ensureDefaultTypes() = Unit
    override suspend fun migrateLegacyTeaRecords(): Int = 0
    override suspend fun setTypeEnabled(code: String, enabled: Boolean) = Unit
    override suspend fun reorderTypes(types: List<TrackType>) = Unit
}
