package com.example.lifetrack.core.model

/**
 * 亲密关系详情字段的唯一来源：备份规格校验、编辑器输入校验与详情展示共用，
 * 避免枚举或范围在多处漂移。值统一存字符串，落在通用 `event_detail` 表。
 *
 * 台账 A10 按数据最小化只记录三项可空的中性字段，不记录伴侣姓名或任何
 * 可识别身份的信息。三个字段均可空：未填写的键不写入详情 map，恢复路径
 * 同样接受 `detail = null`，展示层统一用「未记录」占位。
 */
object IntimacyFields {
    const val TYPE_CODE = "intimacy"
    const val PROTECTION = "protection"
    const val EXPERIENCE = "experience"
    const val DURATION = "durationMin"

    /** 与运动模块同口径：单次上限 24 小时，超过基本是误输入。 */
    val DURATION_RANGE = 1..1440
    val PROTECTION_VALUES = listOf("yes", "no", "na")
    val EXPERIENCE_VALUES = listOf("good", "okay", "bad")

    fun isValidDuration(value: String): Boolean =
        value.toIntOrNull()?.let { it in DURATION_RANGE } == true

    fun protectionLabel(value: String): String = when (value) {
        "yes" -> "是"
        "no" -> "否"
        "na" -> "不适用"
        else -> "未记录"
    }

    fun experienceLabel(value: String): String = when (value) {
        "good" -> "好"
        "okay" -> "一般"
        "bad" -> "差"
        else -> "未记录"
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
