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

/** 首个走通用详情表的新类型，用来验证 A3–A5 铺好的扩展点。 */
val DEFAULT_WATER_TYPE = TrackTypeEntity(
    code = "water",
    name = "饮水",
    iconKey = "water",
    category = "drink",
    trackingMode = "EVENT",
    enabled = true,
    sortOrder = 20,
    schemaVersion = 1
)

/** 第二个走通用详情表的新类型；咖啡与茶用同一个类型，靠 drinkType 字段区分。 */
val DEFAULT_COFFEE_TYPE = TrackTypeEntity(
    code = "coffee",
    name = "咖啡 / 茶",
    iconKey = "coffee",
    category = "drink",
    trackingMode = "EVENT",
    enabled = true,
    sortOrder = 30,
    schemaVersion = 1
)

/** 第三个走通用详情表的新类型；只注册类型与字段规格，沿用 A6 的扩展点。 */
val DEFAULT_SPORT_TYPE = TrackTypeEntity(
    code = "sport",
    name = "运动",
    iconKey = "sport",
    category = "activity",
    trackingMode = "EVENT",
    enabled = true,
    sortOrder = 40,
    schemaVersion = 1
)

/** 新增内置类型时只改这里，ensureDefaultTypes 与备份恢复兜底会一起生效。 */
val DEFAULT_TYPES: List<TrackTypeEntity> = listOf(
    DEFAULT_POOP_TYPE,
    DEFAULT_WATER_TYPE,
    DEFAULT_COFFEE_TYPE,
    DEFAULT_SPORT_TYPE
)
