package com.example.lifetrack.core.model

/**
 * 茶详情字段的唯一来源。v0.7 起茶从 coffee 类型里独立出来，成为自己的类型。
 *
 * 字段很克制：茶种、几杯、多少毫升、热量。没有咖啡的杯型 / 豆子 / 糖度那一套。
 * 茶种允许自定义，值域动态，只做长度校验。值统一存字符串，落在通用 `event_detail` 表。
 */
object TeaFields {
    const val TYPE_CODE = "tea"

    const val VARIETY = "teaVariety"
    const val CUPS = "cups"
    const val AMOUNT = "amountMl"
    const val CALORIES = "calories"

    val CUPS_RANGE = 1..20
    val AMOUNT_RANGE = 1..5000
    val CALORIES_RANGE = 0..3000
    const val MAX_VARIETY_LENGTH = 40

    val PRESET_VARIETIES = listOf("茉莉花茶", "绿茶", "黑茶", "红茶", "乌龙茶", "普洱茶")

    /** 当前类型允许出现的全部键；与 [DetailSpec] 白名单同源。 */
    val ALL_KEYS = setOf(VARIETY, CUPS, AMOUNT, CALORIES)

    fun isValidCups(value: String): Boolean =
        value.toIntOrNull()?.let { it in CUPS_RANGE } == true

    fun isValidAmount(value: String): Boolean =
        value.toIntOrNull()?.let { it in AMOUNT_RANGE } == true

    fun isValidCalories(value: String): Boolean =
        value.toIntOrNull()?.let { it in CALORIES_RANGE } == true

    fun isValidVariety(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_VARIETY_LENGTH

    /** 茶普遍低热量，给个保守默认；自定义茶种没有估值，返回 null。 */
    fun defaultCalories(variety: String): Int? = when (variety) {
        "茉莉花茶", "绿茶", "黑茶", "红茶", "乌龙茶", "普洱茶" -> 5
        else -> null
    }
}
