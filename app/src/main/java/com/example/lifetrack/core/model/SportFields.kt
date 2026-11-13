package com.example.lifetrack.core.model

/**
 * 运动详情字段的唯一来源：备份规格校验、编辑器输入校验与列表展示共用，
 * 避免范围或枚举在多处漂移。值统一存字符串，落在通用 `event_detail` 表。
 *
 * 台账 A8 只要求类型、时长、强度三项，距离 / 配速等留到确有需要时再加，
 * 新增字段只需扩 [SPORTS] / [INTENSITIES] 与规格表，不必升 Room 版本。
 *
 * 运动子应用（v0.7）把界面收敛到瑜伽 / 骑行 / 跑步三类，但**只收敛选择面**：
 * [LEGACY_SPORTS] 里的四个旧值仍保留标签映射与备份校验白名单。原因是
 * `DetailSpec` 用 [ALL_SPORT_VALUES] 做业务校验，直接删值会让任何含旧类型记录的
 * 备份整份恢复失败；已落库的历史记录也会因为缺标签退化成「运动」。
 */
object SportFields {
    const val TYPE_CODE = "sport"
    const val SPORT = "sportType"
    const val DURATION = "durationMin"
    const val INTENSITY = "intensity"

    /** 单次运动上限 24 小时：超过它基本是误输入，而不是真实记录。 */
    val DURATION_RANGE = 1..1440

    /** 选择器、快记卡与类型筛选只展示这三类，顺序即界面顺序。 */
    val SPORTS = listOf("yoga", "ride", "run")

    /**
     * 已从界面下线、但历史记录与旧备份里仍可能出现的值。
     * 只读保留：不进入任何选择器，也不允许新记录再用。
     */
    val LEGACY_SPORTS = listOf("walk", "swim", "strength", "other")

    /** 备份校验与标签映射的完整白名单：新值 + 旧值。 */
    val ALL_SPORT_VALUES = SPORTS + LEGACY_SPORTS

    val INTENSITIES = listOf("low", "medium", "high")

    /** 编辑旧记录时用来提示「这个类型已下线」，不允许静默改值。 */
    fun isLegacy(value: String): Boolean = value in LEGACY_SPORTS

    fun isSelectable(value: String): Boolean = value in SPORTS

    fun isValidDuration(value: String): Boolean =
        value.toIntOrNull()?.let { it in DURATION_RANGE } == true

    /** 旧值的分支只增不减，否则历史记录会一起退化成「运动」。 */
    fun sportLabel(value: String): String = when (value) {
        "yoga" -> "瑜伽"
        "ride" -> "骑行"
        "run" -> "跑步"
        "walk" -> "步行"
        "swim" -> "游泳"
        "strength" -> "力量"
        "other" -> "其他"
        else -> "运动"
    }

    fun intensityLabel(value: String): String = when (value) {
        "low" -> "低强度"
        "high" -> "高强度"
        "medium" -> "中等强度"
        else -> "强度未记录"
    }

    /** 45 → 「45 分钟」；80 → 「1 小时 20 分」；120 → 「2 小时」。 */
    fun durationLabel(value: String): String {
        val minutes = value.toIntOrNull() ?: return "时长未记录"
        return minutesLabel(minutes)
    }

    /** 与 [durationLabel] 同口径，但直接收分钟数，供派生统计复用。 */
    fun minutesLabel(minutes: Int): String {
        if (minutes <= 0) return "0 分钟"
        if (minutes < 60) return "$minutes 分钟"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "$hours 小时" else "$hours 小时 $rest 分"
    }
}
