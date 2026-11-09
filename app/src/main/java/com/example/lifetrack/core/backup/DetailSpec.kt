package com.example.lifetrack.core.backup

import com.example.lifetrack.core.model.WaterFields

/**
 * 通用详情的字段规格。
 *
 * 结构级校验（key 命名、数量、长度）在 [DetailLimits]，业务级规则由各类型在此注册。
 * A6 起注册饮水；A7/A8 加类型只需在这里加一项，不必再动备份格式或 Room schema。
 */
data class DetailSpec(
    val typeCode: String,
    val allowedFields: Set<String> = emptySet(),
    val validators: Map<String, (String) -> Boolean> = emptyMap()
)

object DetailSpecRegistry {
    val builtIn: Map<String, DetailSpec> = mapOf(
        WaterFields.TYPE_CODE to DetailSpec(
            typeCode = WaterFields.TYPE_CODE,
            allowedFields = setOf(WaterFields.AMOUNT, WaterFields.UNIT),
            validators = mapOf(
                WaterFields.AMOUNT to WaterFields::isValidAmount,
                WaterFields.UNIT to { it in WaterFields.UNITS }
            )
        )
    )

    fun specFor(typeCode: String, extra: Map<String, DetailSpec> = emptyMap()): DetailSpec? =
        extra[typeCode] ?: builtIn[typeCode]
}

/** 当前版本真正能落库的通用详情类型；未列入的类型在解码时明确拒绝，绝不静默丢弃。 */
val SUPPORTED_DETAIL_TYPES: Set<String> = DetailSpecRegistry.builtIn.keys

/** 解码选项：生产默认放行 [SUPPORTED_DETAIL_TYPES]，单测可注入自定义规格。 */
data class BackupDecodeOptions(
    val supportedDetailTypes: Set<String> = SUPPORTED_DETAIL_TYPES,
    val detailSpecs: Map<String, DetailSpec> = emptyMap()
) {
    companion object {
        val Default = BackupDecodeOptions()
    }
}

internal object DetailLimits {
    const val MAX_FIELDS = 16
    const val MAX_KEY_LENGTH = 32
    const val MAX_VALUE_LENGTH = 256
    private val keyPattern = Regex("[a-z][a-zA-Z0-9_]*")

    /** 结构不合法返回 false；业务级取值由 [DetailSpec.validators] 负责。 */
    fun isStructuralValid(fields: Map<String, String>): Boolean =
        fields.size <= MAX_FIELDS && fields.all { (key, value) ->
            key.length in 1..MAX_KEY_LENGTH &&
                keyPattern.matches(key) &&
                value.length <= MAX_VALUE_LENGTH
        }
}
