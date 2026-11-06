package com.example.lifetrack.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_event",
    foreignKeys = [
        ForeignKey(
            entity = TrackTypeEntity::class,
            parentColumns = ["code"],
            childColumns = ["typeCode"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["typeCode", "localDate"]),
        Index(value = ["occurredAtEpochMillis"])
    ]
)
data class TrackEventEntity(
    @PrimaryKey val id: String,
    val typeCode: String,
    /** UTC instant, Unix epoch milliseconds. */
    val occurredAtEpochMillis: Long,
    /** Calendar date at the place/time zone where the event was recorded. */
    val localDate: String,
    /** IANA time zone, for example Asia/Tokyo. */
    val zoneId: String,
    val note: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
