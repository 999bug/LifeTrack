package com.example.lifetrack.core.model

/**
 * 咖啡 / 茶详情字段的唯一来源：备份规格校验、编辑器输入校验与列表展示共用，
 * 避免范围或枚举在两处漂移。值统一存字符串，落在通用 `event_detail` 表。
 */
object CoffeeFields {
    const val TYPE_CODE = "coffee"
    const val DRINK = "drinkType"
    const val AMOUNT = "amountMl"
    const val UNIT = "unit"
    const val CAFFEINE = "caffeine"

    val AMOUNT_RANGE = 1..2000
    val DRINKS = listOf("coffee", "tea")
    val UNITS = listOf("ml", "cup")
    val CAFFEINE_LEVELS = listOf("yes", "low", "no")

    fun isValidAmount(value: String): Boolean =
        value.toIntOrNull()?.let { it in AMOUNT_RANGE } == true

    fun drinkLabel(value: String): String = when (value) {
        "tea" -> "茶"
        else -> "咖啡"
    }

    fun unitLabel(value: String): String = when (value) {
        "cup" -> "杯"
        else -> "ml"
    }

    fun caffeineLabel(value: String): String = when (value) {
        "low" -> "低因"
        "no" -> "无咖啡因"
        else -> "含咖啡因"
    }
}
