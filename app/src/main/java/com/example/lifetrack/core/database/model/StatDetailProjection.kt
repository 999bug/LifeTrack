package com.example.lifetrack.core.database.model

/**
 * 形态分布用的轻投影：只取详情字段与本地日期，不加载备注、事件 ID 等无关列。
 *
 * LEFT JOIN 后「没有详情」的记录（例如快速打卡）所有详情字段都是 NULL，
 * 三个身体感受字段用 Int? 承接 SQLite 的 0/1，避免可空 Boolean 在投影里的转换歧义。
 */
data class StatDetailProjection(
    val localDate: String,
    val bristolType: Int?,
    val amountCode: Int?,
    val effortCode: Int?,
    val urgency: Int?,
    val abdominalDiscomfort: Int?,
    val incompleteEvacuation: Int?
)
