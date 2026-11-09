package com.example.lifetrack.domain.model

import com.example.lifetrack.core.model.RecordDetail

/** 通用记录草稿；各类型编辑器把自己的强类型草稿转换成它，Repository 按 detail 分派落库。 */
data class RecordDraft(
    val typeCode: String,
    val occurredAtEpochMillis: Long,
    val zoneId: String,
    val note: String = "",
    val detail: RecordDetail? = null
)

fun PoopRecordDraft.toRecordDraft() = RecordDraft(
    typeCode = "poop",
    occurredAtEpochMillis = occurredAtEpochMillis,
    zoneId = zoneId,
    note = note,
    detail = RecordDetail.Poop(
        bristolType = bristolType,
        amountCode = amountCode,
        effortCode = effortCode,
        urgency = urgency,
        abdominalDiscomfort = abdominalDiscomfort,
        incompleteEvacuation = incompleteEvacuation
    )
)
