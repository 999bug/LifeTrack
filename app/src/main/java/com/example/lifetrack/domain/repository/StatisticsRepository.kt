package com.example.lifetrack.domain.repository

import com.example.lifetrack.domain.model.DailyCount
import com.example.lifetrack.domain.model.DailyTypeCount
import com.example.lifetrack.domain.model.EventTimestamp
import com.example.lifetrack.domain.model.StatDetail
import kotlinx.coroutines.flow.Flow

interface StatisticsRepository {
    fun observeDailyCounts(startDate: String, endDate: String, typeCode: String?): Flow<List<DailyCount>>
    fun observeDailyTypeCounts(startDate: String, endDate: String): Flow<List<DailyTypeCount>>
    fun observeEventTimestamps(startDate: String, endDate: String, typeCode: String?): Flow<List<EventTimestamp>>
    fun observeStatDetails(startDate: String, endDate: String): Flow<List<StatDetail>>
}
