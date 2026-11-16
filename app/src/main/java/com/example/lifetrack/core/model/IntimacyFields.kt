package com.example.lifetrack.core.model

/**
 * 亲密关系详情字段的唯一来源：备份规格校验、编辑器输入校验与详情展示共用，
 * 避免枚举或范围在多处漂移。值统一存字符串，落在通用 `event_detail` 表。
 *
 * 0.6.x 起按「措施 + 时间」重构：措施是 10 选 1 的 [METHOD]（与参考界面的
 * 无措施 / 避孕套 / 体外排精 / 未射精 / 避孕药 / 节育环 / 其他 / 自慰一组一致），
 * 时间仍落在事件自身的 `occurredAtEpochMillis`，不另存。
 *
 * [PROTECTION] / [EXPERIENCE] / [DURATION] 是旧版（≤0.6.13）的三项字段，
 * **保留只为兼容已存在的记录与备份**：不再提供输入，值域校验与展示标签保持原样，
 * 否则老备份恢复时会因为「未知字段」被整体拒绝。
 *
 * 本模块不记录伴侣姓名或任何可识别身份的信息。
 */
object IntimacyFields {
    const val TYPE_CODE = "intimacy"

    /** 重构后的主字段：避孕 / 防护措施，10 选 1。 */
    const val METHOD = "method"

    const val PROTECTION = "protection"
    const val EXPERIENCE = "experience"
    const val DURATION = "durationMin"

    /** 与运动模块同口径：单次上限 24 小时，超过基本是误输入。 */
    val DURATION_RANGE = 1..1440
    val PROTECTION_VALUES = listOf("yes", "no", "na")
    val EXPERIENCE_VALUES = listOf("good", "okay", "bad")

    /**
     * 措施白名单。顺序即界面顺序：前 5 项是避孕方式，其后是药物与器械，
     * 最后两项是「其他」与自慰。code 稳定供备份往返，label 仅用于展示。
     */
    val METHOD_OPTIONS = listOf(
        "none" to "无措施",
        "condom" to "避孕套",
        "withdrawal" to "体外排精",
        "noEjaculation" to "未射精",
        "emergencyPill" to "紧急避孕药",
        "shortPill" to "短效避孕药",
        "longPill" to "长效避孕药",
        "iud" to "节育环",
        "other" to "其他措施",
        "masturbation" to "自慰"
    )
    val METHOD_VALUES = METHOD_OPTIONS.map { it.first }

    fun isValidMethod(value: String): Boolean = value in METHOD_VALUES

    fun isValidDuration(value: String): Boolean =
        value.toIntOrNull()?.let { it in DURATION_RANGE } == true

    fun methodLabel(value: String): String =
        METHOD_OPTIONS.firstOrNull { it.first == value }?.second ?: "未记录"

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
