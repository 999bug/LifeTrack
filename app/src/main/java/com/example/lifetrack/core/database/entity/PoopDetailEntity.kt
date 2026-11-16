package com.example.lifetrack.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "poop_detail",
    foreignKeys = [
        ForeignKey(
            entity = TrackEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PoopDetailEntity(
    @PrimaryKey val eventId: String,
    val bristolType: Int? = null,
    /** 1 = small, 2 = medium, 3 = large. */
    val amountCode: Int? = null,
    /** 1 = no effort, 2 = mild effort, 3 = significant effort. */
    val effortCode: Int? = null,
    val urgency: Boolean? = null,
    val abdominalDiscomfort: Boolean? = null,
    val incompleteEvacuation: Boolean? = null,
    /** v4：第二次进入时记下的结束时刻；手动填用时或仍在进行中时为 null。 */
    val endedAtEpochMillis: Long? = null,
    /** v4：最终用时（1–60 分钟）；超过阈值被判无效时保持 null。 */
    val durationMinutes: Int? = null
)
