package com.example.lifetrack.core.model

import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity

/**
 * 一条事件的可选详情。
 *
 * 排便沿用强类型的 [Poop]（六个可空字段已有范围校验）；后续模块用 [Generic]，
 * 字段统一为字符串，值域由 `DetailSpecRegistry` 按 typeCode 分派校验。
 * 这样备份快照不再依赖 Room 的 `PoopRecordRelation`，新类型不需要改动格式层。
 */
sealed interface RecordDetail {
    data class Poop(
        val bristolType: Int? = null,
        val amountCode: Int? = null,
        val effortCode: Int? = null,
        val urgency: Boolean? = null,
        val abdominalDiscomfort: Boolean? = null,
        val incompleteEvacuation: Boolean? = null
    ) : RecordDetail

    data class Generic(
        val typeCode: String,
        val fields: Map<String, String> = emptyMap()
    ) : RecordDetail
}

/** 导出/恢复用的完整一条记录，与 Room 关系类解耦。 */
data class BackupRecord(
    val event: TrackEventEntity,
    val detail: RecordDetail? = null
)

fun PoopDetailEntity.toRecordDetail() = RecordDetail.Poop(
    bristolType = bristolType,
    amountCode = amountCode,
    effortCode = effortCode,
    urgency = urgency,
    abdominalDiscomfort = abdominalDiscomfort,
    incompleteEvacuation = incompleteEvacuation
)

fun RecordDetail.Poop.toEntity(eventId: String) = PoopDetailEntity(
    eventId = eventId,
    bristolType = bristolType,
    amountCode = amountCode,
    effortCode = effortCode,
    urgency = urgency,
    abdominalDiscomfort = abdominalDiscomfort,
    incompleteEvacuation = incompleteEvacuation
)
