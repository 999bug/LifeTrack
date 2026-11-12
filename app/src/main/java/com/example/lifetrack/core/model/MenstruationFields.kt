package com.example.lifetrack.core.model

/**
 * 月经详情字段的唯一来源：备份规格校验、编辑器输入校验与详情展示共用，
 * 避免枚举或白名单在多处漂移。值统一存字符串，落在通用 `event_detail` 表。
 *
 * 台账 A9 按数据最小化只记录五项可空的中性体征字段，不记录伴侣 / 第三方身份、
 * 图片、富文本或位置。五个字段均可空：未填写的键不写入详情 map，恢复路径同样
 * 接受 `detail = null`，展示层统一用「未记录」占位。日记是 UI 上的第六行，数据上
 * 复用 `TrackEventEntity.note`，不在这里新增 detail key（见设计 §5.3 / P4）。
 */
object MenstruationFields {
    const val TYPE_CODE = "menstruation"
    const val FLOW = "flow"
    const val PAIN = "pain"
    const val SYMPTOMS = "symptoms"
    const val MOOD = "mood"
    const val DISCHARGE = "discharge"

    val FLOW_VALUES = listOf("spotting", "light", "medium", "heavy")
    val PAIN_VALUES = listOf("none", "mild", "moderate", "severe")
    val MOOD_VALUES = listOf("calm", "happy", "sensitive", "low", "irritable")
    val DISCHARGE_VALUES = listOf("dry", "sticky", "creamy", "watery", "eggwhite")

    /** 症状固定 12 项白名单，code 稳定供备份往返，label 仅用于展示。 */
    val SYMPTOM_OPTIONS = listOf(
        "cramps" to "腹痛",
        "backache" to "腰酸",
        "headache" to "头痛",
        "breastTenderness" to "乳房胀痛",
        "bloating" to "腹胀",
        "nausea" to "恶心",
        "fatigue" to "乏力",
        "insomnia" to "失眠",
        "moodSwings" to "情绪波动",
        "appetiteChange" to "食欲变化",
        "acne" to "痤疮",
        "dizziness" to "头晕"
    )
    val SYMPTOM_CODES = SYMPTOM_OPTIONS.map { it.first }

    /** 多选上限与串长上限：与 `DetailLimits.MAX_VALUE_LENGTH` 同口径，防止脏值撑爆通用详情。 */
    const val MAX_SYMPTOMS = 8
    const val MAX_SYMPTOMS_LENGTH = 256
    private const val SYMPTOM_SEPARATOR = "|"

    fun isValidFlow(value: String): Boolean = value in FLOW_VALUES
    fun isValidPain(value: String): Boolean = value in PAIN_VALUES
    fun isValidMood(value: String): Boolean = value in MOOD_VALUES
    fun isValidDischarge(value: String): Boolean = value in DISCHARGE_VALUES

    /**
     * `symptoms` 存 `|` 分隔的 code 串：每个 token 必须在白名单、无重复、≤ 8 项、串长 ≤ 256。
     * 空串视为非法（未选症状时编辑器根本不写这个键），与「未填写不入库」一致。
     */
    fun isValidSymptoms(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_SYMPTOMS_LENGTH) return false
        val tokens = value.split(SYMPTOM_SEPARATOR)
        if (tokens.size > MAX_SYMPTOMS) return false
        if (tokens.size != tokens.toSet().size) return false
        return tokens.all { it in SYMPTOM_CODES }
    }

    fun encodeSymptoms(codes: List<String>): String = codes.joinToString(SYMPTOM_SEPARATOR)

    fun decodeSymptoms(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SYMPTOM_SEPARATOR)

    fun flowLabel(value: String): String = when (value) {
        "spotting" -> "点滴"
        "light" -> "少"
        "medium" -> "中"
        "heavy" -> "多"
        else -> "未记录"
    }

    fun painLabel(value: String): String = when (value) {
        "none" -> "无"
        "mild" -> "轻"
        "moderate" -> "中"
        "severe" -> "重"
        else -> "未记录"
    }

    fun moodLabel(value: String): String = when (value) {
        "calm" -> "平静"
        "happy" -> "愉快"
        "sensitive" -> "敏感"
        "low" -> "低落"
        "irritable" -> "易怒"
        else -> "未记录"
    }

    fun dischargeLabel(value: String): String = when (value) {
        "dry" -> "干燥"
        "sticky" -> "黏稠"
        "creamy" -> "乳白"
        "watery" -> "水样"
        "eggwhite" -> "蛋清"
        else -> "未记录"
    }

    fun symptomLabel(code: String): String =
        SYMPTOM_OPTIONS.firstOrNull { it.first == code }?.second ?: "未知症状"
}