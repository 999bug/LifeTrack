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
    val note: String = ""
)
