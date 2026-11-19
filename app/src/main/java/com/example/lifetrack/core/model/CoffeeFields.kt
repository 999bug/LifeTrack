package com.example.lifetrack.core.model

/**
 * 咖啡详情字段的唯一来源：备份规格校验、编辑器输入校验与列表展示共用，
 * 避免范围或枚举在多处漂移。值统一存字符串，落在通用 `event_detail` 表。
 *
 * v0.7 起咖啡与茶拆成两个独立类型：本对象只描述 [TYPE_CODE] = coffee 的字段，
 * 茶见 [TeaFields]。旧版本把茶记在 coffee 类型里、靠 `drinkType=tea` 区分，
 * 因此 [DRINK] / [CAFFEINE] 等旧键仍留在 [ALL_KEYS] 与白名单里，保证历史记录与
 * 旧备份仍能解码、展示；新编辑器不再写入这些旧键。
 */
object CoffeeFields {
    const val TYPE_CODE = "coffee"

    // —— 新字段 ——
    /** 品种名（如「橙C美式」）；用户可自定义，值域动态，只做长度校验。 */
    const val VARIETY = "variety"
    const val SIZE = "cupSize"
    const val TEMPERATURE = "temperature"
    const val BEANS = "beans"
    const val CONCENTRATION = "concentration"
    const val SUGAR = "sugar"
    const val CREAM = "cream"
    /** 热量，单位大卡；选填。 */
    const val CALORIES = "calories"

    // —— 旧字段：仅为兼容历史记录 / 备份，新记录不再写入 ——
    const val DRINK = "drinkType"
    const val AMOUNT = "amountMl"
    const val UNIT = "unit"
    const val CAFFEINE = "caffeine"

    val AMOUNT_RANGE = 1..2000
    /** 单杯热量上限，超过它基本是误输入。 */
    val CALORIES_RANGE = 0..3000
    /** 品种名最大长度，防止备份里塞超长显示文本。 */
    const val MAX_VARIETY_LENGTH = 40

    /** 杯型 → 毫升，编辑器选中杯型时带出份量，仍允许用户改。 */
    val SIZE_ML = mapOf("medium" to 350, "large" to 450, "xlarge" to 596)

    val SIZES = SIZE_ML.keys.toList()
    val TEMPERATURES = listOf("ice", "hot")
    val BEANS_VALUES = listOf("espresso", "dark")
    val CONCENTRATIONS = listOf("normal", "extra")
    val SUGARS = listOf("standard", "less", "lighter", "micro", "none")
    val CREAMS = listOf("none", "added")

    /** 预设品种，顺序即界面顺序；用户自定义品种另存，可追加到其后。 */
    val PRESET_VARIETIES = listOf("经典美式", "橙C美式", "小黄油美式", "生椰拿铁", "拿铁", "澳白")

    /** 常见品种的默认热量（大卡）；自定义品种没有估值，返回 null 由用户手填。 */
    private val DEFAULT_CALORIES = mapOf(
        "经典美式" to 15,
        "橙C美式" to 95,
        "小黄油美式" to 150,
        "生椰拿铁" to 190,
        "拿铁" to 180,
        "澳白" to 130
    )

    /** 当前类型允许出现的全部键，含兼容旧键；与 [DetailSpec] 白名单同源。 */
    val ALL_KEYS = setOf(
        VARIETY, SIZE, TEMPERATURE, BEANS, CONCENTRATION, SUGAR, CREAM, CALORIES,
        DRINK, AMOUNT, UNIT, CAFFEINE
    )

    fun isValidAmount(value: String): Boolean =
        value.toIntOrNull()?.let { it in AMOUNT_RANGE } == true

    fun isValidCalories(value: String): Boolean =
        value.toIntOrNull()?.let { it in CALORIES_RANGE } == true

    /** 品种名可自定义，只要求非空且不过长；不设白名单。 */
    fun isValidVariety(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_VARIETY_LENGTH

    fun defaultCalories(variety: String): Int? = DEFAULT_CALORIES[variety]

    fun sizeLabel(value: String): String = when (value) {
        "medium" -> "中杯"
        "large" -> "大杯"
        "xlarge" -> "超大杯"
        else -> value
    }

    fun temperatureLabel(value: String): String = when (value) {
        "ice" -> "冰"
        "hot" -> "热"
        else -> value
    }

    fun beansLabel(value: String): String = when (value) {
        "espresso" -> "意式拼配"
        "dark" -> "深烘拼配"
        else -> value
    }

    fun concentrationLabel(value: String): String = when (value) {
        "extra" -> "加浓1份浓缩"
        else -> "默认浓度"
    }

    fun sugarLabel(value: String): String = when (value) {
        "standard" -> "标准甜"
        "less" -> "少甜"
        "lighter" -> "少少甜"
        "micro" -> "微甜"
        "none" -> "不另外加糖"
        else -> value
    }

    fun creamLabel(value: String): String = when (value) {
        "added" -> "加奶油"
        else -> "无奶油"
    }

    // —— 旧值标签：历史记录与旧备份只读展示，不进入任何选择器 ——
    val LEGACY_DRINKS = listOf("coffee", "tea")
    val LEGACY_UNITS = listOf("ml", "cup")
    val LEGACY_CAFFEINE_LEVELS = listOf("yes", "low", "no")

    /** @deprecated 旧枚举别名，只为不破坏历史测试与校验引用；新代码用 LEGACY_*。 */
    val DRINKS get() = LEGACY_DRINKS
    val UNITS get() = LEGACY_UNITS
    val CAFFEINE_LEVELS get() = LEGACY_CAFFEINE_LEVELS

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
        "yes" -> "含咖啡因"
        else -> value
    }
}
