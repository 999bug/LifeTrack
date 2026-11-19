package com.example.lifetrack.domain.repository

import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.DailyTypeCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.StatDetailProjection
import kotlinx.coroutines.flow.Flow

interface StatisticsRepository {
    fun observeDailyCounts(startDate: String, endDate: String, typeCode: String?): Flow<List<DailyCountProjection>>
    fun observeDailyTypeCounts(startDate: String, endDate: String): Flow<List<DailyTypeCountProjection>>
    fun observeEventTimestamps(startDate: String, endDate: String, typeCode: String?): Flow<List<EventTimestamp>>
    fun observeStatDetails(startDate: String, endDate: String): Flow<List<StatDetailProjection>>
}
