package com.example.lifetrack.core.database.model

/**
 * 统计只需要「事件发生的时刻 + 当时的时区」。
 * 单独建投影而不是复用 PoopRecordRelation，是为了避免统计时把 detail 一起查出来。
 */
data class EventTimestamp(
    val occurredAtEpochMillis: Long,
    val zoneId: String
)
