package com.example.lifetrack.domain.repository

import com.example.lifetrack.domain.model.TrackType
import kotlinx.coroutines.flow.Flow

interface TypeCatalogRepository {
    fun observeTrackTypes(): Flow<List<TrackType>>
    suspend fun ensureDefaultTypes()
    suspend fun migrateLegacyTeaRecords(): Int
    suspend fun setTypeEnabled(code: String, enabled: Boolean)
    suspend fun reorderTypes(types: List<TrackType>)
}
