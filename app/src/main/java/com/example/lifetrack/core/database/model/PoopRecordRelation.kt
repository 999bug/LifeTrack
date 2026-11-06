package com.example.lifetrack.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity

/** One generic event with its optional feature-specific detail. */
data class PoopRecordRelation(
    @Embedded val event: TrackEventEntity,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val detail: PoopDetailEntity?
)

data class DailyCountProjection(
    val localDate: String,
    val count: Int
)
