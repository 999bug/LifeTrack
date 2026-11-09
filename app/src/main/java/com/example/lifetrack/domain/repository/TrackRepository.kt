package com.example.lifetrack.domain.repository

import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.domain.model.PoopRecordDraft
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun observePoopRecordsForDate(localDate: String): Flow<List<PoopRecordRelation>>
    fun observePoopRecordsInRange(startDate: String, endDate: String): Flow<List<PoopRecordRelation>>
    fun observeDailyCounts(startDate: String, endDate: String): Flow<List<DailyCountProjection>>
    fun observeEventTimestamps(startDate: String, endDate: String): Flow<List<EventTimestamp>>
    suspend fun addPoopRecord(draft: PoopRecordDraft): String
    suspend fun updatePoopRecord(eventId: String, draft: PoopRecordDraft)
    suspend fun deletePoopRecord(eventId: String)
}
