package com.example.lifetrack.core.model

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset

/** 历史页当前展示的区间。[filtered] 为 true 表示用户显式选了日期范围，此时不提供「加载更早」。 */
data class HistoryQuery(
    val start: LocalDate,
    val end: LocalDate,
    val filtered: Boolean
)

data class HistoryFilter(val start: LocalDate, val end: LocalDate)

/**
 * Material3 日期选择器内部用的是「UTC 零点的毫秒」。
 * 直接拿它当本地时间用会整体差一天，必须显式按 UTC 解析。
 */
object PickerDateMapper {

    fun toUtcMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromUtcMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
