package com.example.lifetrack.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.lifetrack.core.database.entity.EventDetailEntity
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity

/**
 * 一条通用事件 + 两种可选详情。
 * 排便详情在 `poop_detail`（强类型列），其余类型在 `event_detail`（JSON payload）；
 * 同一个事件不会同时有两份，写入侧按 typeCode 分派。
 */
data class EventWithDetails(
    @Embedded val event: TrackEventEntity,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val poop: PoopDetailEntity?,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val generic: EventDetailEntity?
)
