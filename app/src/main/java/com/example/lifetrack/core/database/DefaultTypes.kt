package com.example.lifetrack.core.database

import com.example.lifetrack.core.database.entity.TrackTypeEntity

/** 首次启动与恢复备份时都要保证存在的内置类型。 */
val DEFAULT_POOP_TYPE = TrackTypeEntity(
    code = "poop",
    name = "排便",
    iconKey = "poop",
    category = "body",
    trackingMode = "EVENT",
    enabled = true,
    sortOrder = 10,
    schemaVersion = 1
)
