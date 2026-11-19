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

import com.example.lifetrack.core.database.model.EventWithDetails
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.RecordDetailCodec
import com.example.lifetrack.core.model.RecordTimeMapper
import com.example.lifetrack.core.model.TeaFields
import com.example.lifetrack.core.model.TeaMigration
import com.example.lifetrack.core.model.toEntity
import com.example.lifetrack.core.model.toRecordDetail
import com.example.lifetrack.domain.model.DailyCount
import com.example.lifetrack.domain.model.DailyTypeCount
import com.example.lifetrack.domain.model.EventTimestamp
import com.example.lifetrack.domain.model.HistoryPage
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordCursor
import com.example.lifetrack.domain.model.StatDetail
import com.example.lifetrack.domain.model.TrackType
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class RoomTrackRepository(
    private val database: AppDatabase
) : TrackRepository {
    private val dao = database.trackEventDao()

    override fun observeRecordsForDate(localDate: String): Flow<List<BackupRecord>> =
        dao.observeRecordsForDate(localDate).map { rows -> rows.map { it.toBackupRecord() } }

    override fun observeRecordsInRange(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<BackupRecord>> =
        dao.observeRecordsInRange(startDate, endDate, typeCode).map { rows ->
            rows.map { it.toBackupRecord() }
        }

    override fun observeHistoryPage(
        startDate: String,
        endDate: String,
        typeCode: String?,
        cursor: RecordCursor?,
        limit: Int
    ): Flow<HistoryPage> =
        dao.observeHistoryPage(
            startDate = startDate,
            endDate = endDate,
            typeCode = typeCode,
            cursorAt = cursor?.occurredAtEpochMillis,
            cursorId = cursor?.id,
            limit = limit + 1
        ).map { rows ->
            val pageRows = rows.take(limit)
            HistoryPage(
                records = pageRows.map { it.toBackupRecord() },
                nextCursor = pageRows.lastOrNull()?.event?.let { event ->
                    RecordCursor(event.occurredAtEpochMillis, event.id)
                },
                hasMore = rows.size > limit
            )
        }
    override fun observeDailyCounts(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<DailyCount>> = dao.observeDailyCounts(startDate, endDate, typeCode)
        .map { rows -> rows.map { DailyCount(it.localDate, it.count) } }

    override fun observeDailyTypeCounts(
        startDate: String,
        endDate: String
    ): Flow<List<DailyTypeCount>> = dao.observeDailyTypeCounts(startDate, endDate)
        .map { rows ->
            rows.map { DailyTypeCount(it.localDate, it.typeCode, it.count, it.firstAt) }
        }

    override fun observeEventTimestamps(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<EventTimestamp>> = dao.observeEventTimestamps(startDate, endDate, typeCode)
        .map { rows -> rows.map { EventTimestamp(it.occurredAtEpochMillis, it.zoneId) } }

    override fun observeStatDetails(
        startDate: String,
        endDate: String
    ): Flow<List<StatDetail>> = dao.observeStatDetails(startDate, endDate)
        .map { rows ->
            rows.map {
                StatDetail(
                    localDate = it.localDate,
                    bristolType = it.bristolType,
                    amountCode = it.amountCode,
                    effortCode = it.effortCode,
                    urgency = it.urgency,
                    abdominalDiscomfort = it.abdominalDiscomfort,
                    incompleteEvacuation = it.incompleteEvacuation
                )
            }
        }

    override fun observeTrackTypes(): Flow<List<TrackType>> =
        database.trackTypeDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun ensureDefaultTypes() {
        val typeDao = database.trackTypeDao()
        typeDao.insertIgnoreAll(DEFAULT_TYPES)
        // v0.7.1 及更早的既有库仍可能保存旧名；只替换这一条展示名，保留启用与排序。
        val coffeeType = DEFAULT_TYPES.first { it.code == CoffeeFields.TYPE_CODE }
        typeDao.renameIfCurrentName(
            code = CoffeeFields.TYPE_CODE,
            legacyName = "咖啡 / 茶",
            newName = coffeeType.name
        )
    }

    override suspend fun migrateLegacyTeaRecords(): Int {
        // 逐条扫描所有带详情的记录，只挑出 drinkType=tea 的旧 coffee 记录。
        // 用 getAllRecords（已带 generic 详情）而非新增 SQL，避免 payload 的 JSON 解析下推到 SQLite。
        val legacy = dao.getAllRecords().mapNotNull { row ->
            val generic = row.generic ?: return@mapNotNull null
            if (row.event.typeCode != CoffeeFields.TYPE_CODE || generic.typeCode != CoffeeFields.TYPE_CODE) {
                return@mapNotNull null
            }
            val fields = RecordDetailCodec.decode(generic.payload) ?: return@mapNotNull null
            val converted = TeaMigration.convertLegacyTea(fields) ?: return@mapNotNull null
            Triple(row.event.copy(typeCode = TeaFields.TYPE_CODE), converted, row.event.id)
        }
        if (legacy.isEmpty()) return 0
        database.withTransaction {
            // tea 父行必须先存在，track_event.typeCode 有 RESTRICT 外键指向它。
            database.trackTypeDao().insertIgnore(
                DEFAULT_TYPES.first { it.code == TeaFields.TYPE_CODE }
            )
            legacy.forEach { (event, fields, eventId) ->
                dao.updateEvent(event)
                dao.putEventDetail(
                    com.example.lifetrack.core.database.entity.EventDetailEntity(
                        eventId = eventId,
                        typeCode = TeaFields.TYPE_CODE,
                        payload = RecordDetailCodec.encode(fields)
                    )
                )
            }
        }
        return legacy.size
    }

    override suspend fun setTypeEnabled(code: String, enabled: Boolean) {
        database.trackTypeDao().setEnabled(code, enabled)
    }

    override suspend fun reorderTypes(types: List<TrackType>) {
        database.withTransaction { database.trackTypeDao().updateAll(types.map { it.toEntity() }) }
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
        // 旧备份把茶记在 coffee 类型里（drinkType=tea），恢复时按 v0.7 口径就地转成 tea，
        // 保证「备份恢复也兼容」。事件与详情一起转，避免两边 typeCode 不一致。
        val restoredEvents = ArrayList<com.example.lifetrack.core.database.entity.TrackEventEntity>()
        val poopDetails = ArrayList<PoopDetailEntity>()
        val genericDetails = ArrayList<EventDetailEntity>()
        payload.events.forEach { record ->
            var event = record.event
            when (val detail = record.detail) {
                null -> Unit
                is RecordDetail.Poop -> poopDetails += detail.toEntity(record.event.id)
                is RecordDetail.Generic -> {
                    require(detail.typeCode == record.event.typeCode) {
                        "详情类型与事件类型不一致：${detail.typeCode}/${record.event.typeCode}"
                    }
                    var fields = detail.fields
                    var typeCode = detail.typeCode
                    if (typeCode == CoffeeFields.TYPE_CODE) {
                        TeaMigration.convertLegacyTea(fields)?.let { converted ->
                            fields = converted
                            typeCode = TeaFields.TYPE_CODE
                            event = event.copy(typeCode = TeaFields.TYPE_CODE)
                        }
                    }
                    genericDetails += EventDetailEntity(
                        eventId = record.event.id,
                        typeCode = typeCode,
                        payload = RecordDetailCodec.encode(fields)
                    )
                }
            }
            restoredEvents += event
        }
        database.withTransaction {
            // 先删事件（两类详情随外键级联），再重建类型，顺序反了会触发外键约束
            dao.deleteAllEvents()
            database.trackTypeDao().deleteAll()
            val types = payload.types.toMutableList()
            DEFAULT_TYPES.forEach { default ->
                if (types.none { it.code == default.code }) types += default
            }
            // 老备份的 coffee 类型名可能还是「咖啡 / 茶」；恢复后它只存咖啡，统一改名。
            val renamed = types.map {
                if (it.code == CoffeeFields.TYPE_CODE) {
                    it.copy(name = DEFAULT_TYPES.first { d -> d.code == CoffeeFields.TYPE_CODE }.name)
                } else it
            }
            database.trackTypeDao().insertAll(renamed)
            dao.insertEvents(restoredEvents)
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

    private fun TrackTypeEntity.toDomain(): TrackType = TrackType(
        code = code,
        name = name,
        iconKey = iconKey,
        category = category,
        trackingMode = trackingMode,
        enabled = enabled,
        sortOrder = sortOrder,
        schemaVersion = schemaVersion
    )

    private fun TrackType.toEntity(): TrackTypeEntity = TrackTypeEntity(
        code = code,
        name = name,
        iconKey = iconKey,
        category = category,
        trackingMode = trackingMode,
        enabled = enabled,
        sortOrder = sortOrder,
        schemaVersion = schemaVersion
    )

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
        incompleteEvacuation = incompleteEvacuation,
        endedAtEpochMillis = endedAtEpochMillis,
        durationMinutes = durationMinutes
    )

    override suspend fun databaseSizeBytes(): Long = withContext(Dispatchers.IO) {
        databaseTotalBytes()
    }

    override suspend fun compactDatabase(): Long = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        // Room 默认 WAL：删除只释放页，占用的空间留在 `-wal` 里，主库可能一直是 4 KB。
        // 做一次 TRUNCATE checkpoint，把日志落盘并截断文件。返回结果第一列是 busy 标志，
        // 非 0 表示还有读事务占着，此时截断无效，明确报错让用户稍后再试。
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            val busy = if (cursor.moveToFirst()) cursor.getInt(0) else -1
            if (busy != 0) error("数据库正被占用，请稍后再试")
        }
        databaseTotalBytes()
    }

    /**
     * 主库 + `-wal` + `-shm` 的合计大小。
     *
     * Room 默认 WAL 模式，新写入先落 `-wal`，主文件可能一直停在 4 KB；
     * 只统计主库会让「整理前后」看起来毫无变化。
     */
    private fun databaseTotalBytes(): Long {
        val basePath = database.openHelper.writableDatabase.path ?: return 0L
        return listOf(basePath, "$basePath-wal", "$basePath-shm")
            .sumOf { File(it).length() }
    }
}
