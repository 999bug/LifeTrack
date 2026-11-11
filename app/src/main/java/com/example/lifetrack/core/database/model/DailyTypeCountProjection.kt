package com.example.lifetrack.core.database.model

/**
 * 某一天某个类型的记录条数，以及该类型当天第一次出现的时间。
 *
 * [firstAt] 只用于排序：月历格子里最多画三个类型图标，取当天最早出现的那三类，
 * 避免每次重组后图标顺序乱跳。按 localDate、firstAt 升序查询即可直接分组。
 */
data class DailyTypeCountProjection(
    val localDate: String,
    val typeCode: String,
    val count: Int,
    val firstAt: Long
)
