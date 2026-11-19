package com.example.lifetrack.core.model

/**
 * 排便用时的唯一口径：起始时间就是事件的 `occurredAtEpochMillis`，结束时间与用时
 * 单独落在 `poop_detail`（v4 新增两列）。
 *
 * 两次进入的语义：
 * - 第一次记录只写开始时间（即事件时刻），结束时间与用时都为空，表示「进行中」；
 * - 再次编辑（或列表里点「结束」）时写结束时间，用时由两者之差自动算出；
 * - 也可以不看结束时间，直接手动填 1–60 分钟，此时结束时间不写入（手填值优先）。
 *
 * 阈值 [MAX_DURATION_MINUTES]：超过一小时的自动用时不成立（基本是忘记结束），
 * 按「本次用时无效」处理——记录本身保留，用时字段留空，由用户手动补。
 *
 * 纯 JVM 对象，不依赖 Android，可被 `testDebugUnitTest` 直接覆盖。
 */
object PoopFields {

    const val TYPE_CODE = "poop"

    /** 自动用时上限：超过一小时不再判定为一次有效记录。 */
    const val MAX_DURATION_MINUTES = 60

    val DURATION_RANGE = 1..MAX_DURATION_MINUTES

    fun isValidManualDuration(value: String): Boolean =
        value.toIntOrNull()?.let { it in DURATION_RANGE } == true

    fun isMeasuredValid(minutes: Int): Boolean = minutes in DURATION_RANGE

    /**
     * 自动用时（整分钟，向上取整）。
     * 同一分钟内结束也算 1 分钟；结束早于开始时返回 0，由 [isMeasuredValid] 判为无效。
     */
    fun measuredMinutes(startEpochMillis: Long, endEpochMillis: Long): Int {
        val delta = endEpochMillis - startEpochMillis
        if (delta <= 0L) return 0
        return ((delta + 59_999L) / 60_000L).toInt()
    }

    /**
     * 存库口径：手填值优先（有效时才采用），否则用结束时间算出的自动值。
     * 手填非法或自动值超阈值时返回「无用时」，绝不写入一个越界的数字。
     */
    fun resolve(
        startEpochMillis: Long,
        endedAtEpochMillis: Long?,
        manualMinutes: String
    ): PoopTiming {
        if (manualMinutes.isNotEmpty()) {
            return if (isValidManualDuration(manualMinutes)) {
                PoopTiming(durationMinutes = manualMinutes.toInt())
            } else {
                PoopTiming()
            }
        }
        if (endedAtEpochMillis == null) return PoopTiming()
        val measured = measuredMinutes(startEpochMillis, endedAtEpochMillis)
        return if (isMeasuredValid(measured)) {
            PoopTiming(durationMinutes = measured, endedAtEpochMillis = endedAtEpochMillis)
        } else {
            PoopTiming()
        }
    }

    /** 列表 / 详情里的用时文案；null 表示没有有效用时。 */
    fun durationLabel(minutes: Int?): String =
        minutes?.let { "$it 分钟" } ?: "未记录用时"
}

/** 一条记录的用时落库结果：[endedAtEpochMillis] 只在自动结束时写入。 */
data class PoopTiming(
    val durationMinutes: Int? = null,
    val endedAtEpochMillis: Long? = null
)
