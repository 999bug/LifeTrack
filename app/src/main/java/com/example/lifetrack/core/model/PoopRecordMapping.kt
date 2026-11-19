package com.example.lifetrack.core.model

import com.example.lifetrack.domain.model.PoopRecord
import com.example.lifetrack.domain.model.RecordEvent

/** 把持久化快照映射为 feature 使用的只读模型，避免 feature 直接导入 Room 实体。 */
fun BackupRecord.toPoopRecord(): PoopRecord = PoopRecord(
    event = RecordEvent(
        id = event.id,
        typeCode = event.typeCode,
        occurredAtEpochMillis = event.occurredAtEpochMillis,
        localDate = event.localDate,
        zoneId = event.zoneId,
        note = event.note,
        createdAtEpochMillis = event.createdAtEpochMillis,
        updatedAtEpochMillis = event.updatedAtEpochMillis
    ),
    detail = detail as? RecordDetail.Poop
)
