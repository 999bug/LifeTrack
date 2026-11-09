package com.example.lifetrack.core.backup

/**
 * 通用详情的字段规格。
 *
 * A5 只做结构级校验（key 命名、数量、长度）并留出注册表扩展点；
 * 业务级规则（数值范围、枚举）由 A6/A7/A8 注册，A12 补齐逐字段校验与坏文件用例。
 */
data class DetailSpec(
    val typeCode: String,
    val allowedFields: Set<String> = emptySet(),
    val validators: Map<String, (String) -> Boolean> = emptyMap()
)

object DetailSpecRegistry {
    /** 当前还没有已落库的通用详情类型；A6 加表后在此注册 water。 */
    val builtIn: Map<String, DetailSpec> = emptyMap()

    fun specFor(typeCode: String, extra: Map<String, DetailSpec> = emptyMap()): DetailSpec? =
        extra[typeCode] ?: builtIn[typeCode]
}

/** 解码选项：生产默认空集，任何通用详情都会被明确拒绝，避免恢复时静默丢数据。 */
data class BackupDecodeOptions(
    val supportedDetailTypes: Set<String> = emptySet(),
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
