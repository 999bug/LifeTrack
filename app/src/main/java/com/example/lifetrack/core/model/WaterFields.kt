package com.example.lifetrack.core.model

/** 饮水详情字段的唯一来源：备份规格校验与编辑器输入校验共用，避免两处范围不一致。 */
object WaterFields {
    const val TYPE_CODE = "water"
    const val AMOUNT = "amountMl"
    const val UNIT = "unit"

    val AMOUNT_RANGE = 1..5000
    val UNITS = listOf("ml", "cup")

    fun isValidAmount(value: String): Boolean =
        value.toIntOrNull()?.let { it in AMOUNT_RANGE } == true

    fun unitLabel(value: String): String = when (value) {
        "cup" -> "杯"
        else -> "ml"
    }
}
