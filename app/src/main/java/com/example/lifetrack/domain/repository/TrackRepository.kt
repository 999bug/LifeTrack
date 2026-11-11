package com.example.lifetrack.domain.repository

import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.database.model.DailyCountProjection
import com.example.lifetrack.core.database.model.DailyTypeCountProjection
import com.example.lifetrack.core.database.model.EventTimestamp
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.database.model.StatDetailProjection
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun observePoopRecordsForDate(localDate: String): Flow<List<PoopRecordRelation>>
    /** 今天页：所有类型的记录，详情已按 typeCode 归一成 [BackupRecord]。 */
    fun observeRecordsForDate(localDate: String): Flow<List<BackupRecord>>
    /** 历史页：按类型过滤的区间记录（null = 全部类型）。 */
    fun observeRecordsInRange(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<BackupRecord>>
    fun observeDailyCounts(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<DailyCountProjection>>

    /** 月历格子：按日期 + 类型聚合，顺序为当天首次出现时间。 */
    fun observeDailyTypeCounts(
        startDate: String,
        endDate: String
    ): Flow<List<DailyTypeCountProjection>>
    fun observeEventTimestamps(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<EventTimestamp>>
    fun observeStatDetails(startDate: String, endDate: String): Flow<List<StatDetailProjection>>
    fun observeTrackTypes(): Flow<List<TrackTypeEntity>>

    /** 首次进入应用时补齐内置类型，已存在的不会被覆盖（保留用户的启用/排序设置）。 */
    suspend fun ensureDefaultTypes()
    suspend fun setTypeEnabled(code: String, enabled: Boolean)
    suspend fun reorderTypes(types: List<TrackTypeEntity>)

    suspend fun addPoopRecord(draft: PoopRecordDraft): String
    suspend fun updatePoopRecord(eventId: String, draft: PoopRecordDraft)
    /** 删除任意类型的事件（两类详情随外键级联）。 */
    suspend fun deletePoopRecord(eventId: String)

    /** 通用写入：按 draft.typeCode 与 detail 分派到 poop_detail 或 event_detail。 */
    suspend fun addRecord(draft: RecordDraft): String
    suspend fun updateRecord(eventId: String, draft: RecordDraft)

    /** 导出用的完整快照。 */
    suspend fun readBackup(): BackupPayload

    /** 整体替换现有数据，必须在事务内完成。 */
    suspend fun restoreBackup(payload: BackupPayload)

    suspend fun countAllRecords(): Int
}
