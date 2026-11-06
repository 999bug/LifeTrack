package com.example.lifetrack.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_type")
data class TrackTypeEntity(
    @PrimaryKey val code: String,
    val name: String,
    val iconKey: String,
    val category: String,
    val trackingMode: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val schemaVersion: Int
)
