package com.example.lifetrack.domain.model

/** 记录类型的领域视图；feature 不再直接依赖 Room 实体。 */
data class TrackType(
    val code: String,
    val name: String,
    val iconKey: String,
    val category: String,
    val trackingMode: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val schemaVersion: Int
)
