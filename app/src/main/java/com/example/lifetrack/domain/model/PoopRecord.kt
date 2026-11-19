package com.example.lifetrack.domain.model

import com.example.lifetrack.core.model.RecordDetail

/** feature 可安全使用的记录事件快照。 */
data class RecordEvent(
    val id: String,
    val typeCode: String,
    val occurredAtEpochMillis: Long,
    val localDate: String,
    val zoneId: String,
    val note: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

/** 单条排便记录的只读领域模型，供编辑器与列表复用。 */
data class PoopRecord(
    val event: RecordEvent,
    val detail: RecordDetail.Poop?
)
