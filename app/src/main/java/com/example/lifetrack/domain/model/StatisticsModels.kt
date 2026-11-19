package com.example.lifetrack.domain.model

/** 统计与月历使用的领域投影；Room 投影只在数据层出现。 */
data class DailyCount(
    val localDate: String,
    val count: Int
)

data class DailyTypeCount(
    val localDate: String,
    val typeCode: String,
    val count: Int,
    val firstAt: Long
)

data class EventTimestamp(
    val occurredAtEpochMillis: Long,
    val zoneId: String
)

data class StatDetail(
    val localDate: String,
    val bristolType: Int?,
    val amountCode: Int?,
    val effortCode: Int?,
    val urgency: Int?,
    val abdominalDiscomfort: Int?,
    val incompleteEvacuation: Int?
)
