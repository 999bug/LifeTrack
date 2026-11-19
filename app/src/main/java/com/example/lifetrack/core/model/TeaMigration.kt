package com.example.lifetrack.core.model

/**
 * 旧版「咖啡 / 茶」单类型拆成两类型时的历史数据修正（纯函数，可单测）。
 *
 * 旧数据形态：一条 typeCode=coffee 的事件，`event_detail.payload` 里
 * `drinkType=tea`，还带 amountMl / unit / caffeine。拆分后茶要用 typeCode=tea、
 * 自己的字段（teaVariety / cups / amountMl / calories）。
 *
 * 转换策略（尽量不丢信息）：
 * - amountMl 原样保留；
 * - unit=cup 时旧值语义是「几杯」，折进 cups；unit=ml（或缺失）时 amountMl 就是毫升；
 *   两者都保留，tea 规格允许 cups 与 amountMl 共存；
 * - 旧 drinkType/unit/caffeine 三个键删掉（tea 规格不认识它们）；
 * - 没有可推断品种，teaVariety 不写，列表显示「未记录品种」。
 *
 * 判定是否需要迁移只看 `drinkType=tea`；coffee 记录一律不动。
 */
object TeaMigration {

    /** 一条 coffee 通用详情是否是「旧茶记录」。 */
    fun isLegacyTea(fields: Map<String, String>): Boolean =
        fields[CoffeeFields.DRINK] == "tea"

    /**
     * 把旧茶详情转成 tea 详情；非旧茶返回 null（调用方据此跳过）。
     */
    fun convertLegacyTea(fields: Map<String, String>): Map<String, String>? {
        if (!isLegacyTea(fields)) return null
        val result = LinkedHashMap<String, String>()
        val amount = fields[CoffeeFields.AMOUNT]
        if (amount != null && TeaFields.isValidAmount(amount)) {
            result[TeaFields.AMOUNT] = amount
        }
        val unit = fields[CoffeeFields.UNIT]
        val rawAmount = fields[CoffeeFields.AMOUNT]?.toIntOrNull()
        if (unit == "cup" && rawAmount != null) {
            val cups = rawAmount.coerceIn(TeaFields.CUPS_RANGE)
            result[TeaFields.CUPS] = cups.toString()
            // 旧 cup 记录没有毫升语义，amountMl 已经按原值写了，这里覆盖成更合理的单杯 250ml
            result[TeaFields.AMOUNT] = (cups * 250).toString().let {
                if (TeaFields.isValidAmount(it)) it else TeaFields.AMOUNT_RANGE.last.toString()
            }
        }
        return result
    }
}
