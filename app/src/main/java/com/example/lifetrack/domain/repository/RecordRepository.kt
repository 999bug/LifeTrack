package com.example.lifetrack.domain.repository

import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import kotlinx.coroutines.flow.Flow

interface RecordRepository {
    fun observePoopRecordsForDate(localDate: String): Flow<List<PoopRecordRelation>>
    fun observeRecordsForDate(localDate: String): Flow<List<BackupRecord>>
    fun observeRecordsInRange(startDate: String, endDate: String, typeCode: String?): Flow<List<BackupRecord>>
    suspend fun addPoopRecord(draft: PoopRecordDraft): String
    suspend fun updatePoopRecord(eventId: String, draft: PoopRecordDraft)
    suspend fun deletePoopRecord(eventId: String)
    suspend fun addRecord(draft: RecordDraft): String
    suspend fun updateRecord(eventId: String, draft: RecordDraft)
}
