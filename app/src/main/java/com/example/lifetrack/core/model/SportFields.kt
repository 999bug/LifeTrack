package com.example.lifetrack.core.model

/**
 * 运动详情字段的唯一来源：备份规格校验、编辑器输入校验与列表展示共用，
 * 避免范围或枚举在多处漂移。值统一存字符串，落在通用 `event_detail` 表。
 *
 * 台账 A8 只要求类型、时长、强度三项，距离 / 配速等留到确有需要时再加，
 * 新增字段只需扩 [SPORTS] / [INTENSITIES] 与规格表，不必升 Room 版本。
 */
object SportFields {
    const val TYPE_CODE = "sport"
    const val SPORT = "sportType"
    const val DURATION = "durationMin"
    const val INTENSITY = "intensity"

    /** 单次运动上限 24 小时：超过它基本是误输入，而不是真实记录。 */
    val DURATION_RANGE = 1..1440
    val SPORTS = listOf("walk", "run", "ride", "swim", "strength", "other")
    val INTENSITIES = listOf("low", "medium", "high")

    fun isValidDuration(value: String): Boolean =
        value.toIntOrNull()?.let { it in DURATION_RANGE } == true

    fun sportLabel(value: String): String = when (value) {
        "walk" -> "步行"
        "run" -> "跑步"
        "ride" -> "骑行"
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
        if (minutes < 60) return "$minutes 分钟"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "$hours 小时" else "$hours 小时 $rest 分"
    }
}
