package com.example.lifetrack.domain.model

data class PoopRecordDraft(
    val occurredAtEpochMillis: Long,
    val zoneId: String,
    val bristolType: Int? = null,
    val amountCode: Int? = null,
    val effortCode: Int? = null,
    val urgency: Boolean? = null,
    val abdominalDiscomfort: Boolean? = null,
    val incompleteEvacuation: Boolean? = null,
    val note: String = "",
    /** 结束时刻；只在自动结束时写入，手动填用时保持 null。 */
    val endedAtEpochMillis: Long? = null,
    /** 最终用时（1–60 分钟），由 [com.example.lifetrack.core.model.PoopFields.resolve] 产出。 */
    val durationMinutes: Int? = null
)
