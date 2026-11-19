package com.example.lifetrack.core.backup

import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.TeaFields
import com.example.lifetrack.core.model.WaterFields

/**
 * 通用详情的字段规格。
 *
 * 结构级校验（key 命名、数量、长度）在 [DetailLimits]，业务级规则由各类型在此注册。
 * A6 起注册饮水、A7 咖啡 / 茶、A8 运动、A10 亲密关系、A9 月经；后续加类型只需在这里加一项，
 * 不必再动备份格式或 Room schema。
 *
 * [requireValidatorsForAllFields] 在构造时强制「声明了字段就必须有校验器」，
 * 防止新增字段只加进 allowedFields 却漏掉 validator，让脏值从备份通道溜进库。
 */
data class DetailSpec(
    val typeCode: String,
    val allowedFields: Set<String> = emptySet(),
    val validators: Map<String, (String) -> Boolean> = emptyMap()
) {
    init {
        require(validators.keys.containsAll(allowedFields)) {
            "类型 $typeCode 的字段缺少校验器：${allowedFields - validators.keys}"
        }
    }

    /** 键必须已声明，且每个值都要通过对应校验器。 */
    fun isFieldsValid(fields: Map<String, String>): Boolean =
        fields.keys.all { it in allowedFields } &&
            fields.all { (key, value) -> validators[key]?.invoke(value) != false }
}

object DetailSpecRegistry {
    val builtIn: Map<String, DetailSpec> = mapOf(
        WaterFields.TYPE_CODE to DetailSpec(
            typeCode = WaterFields.TYPE_CODE,
            allowedFields = setOf(WaterFields.AMOUNT, WaterFields.UNIT),
            validators = mapOf(
                WaterFields.AMOUNT to WaterFields::isValidAmount,
                WaterFields.UNIT to { it in WaterFields.UNITS }
            )
        ),
        CoffeeFields.TYPE_CODE to DetailSpec(
            typeCode = CoffeeFields.TYPE_CODE,
            // 旧键 drinkType/unit/caffeine 保留在白名单：历史记录与旧备份仍要能解码；
            // 品种名是动态值域，只用长度校验，不设枚举白名单。
            allowedFields = CoffeeFields.ALL_KEYS,
            validators = mapOf(
                CoffeeFields.VARIETY to CoffeeFields::isValidVariety,
                CoffeeFields.SIZE to { it in CoffeeFields.SIZES },
                CoffeeFields.TEMPERATURE to { it in CoffeeFields.TEMPERATURES },
                CoffeeFields.BEANS to { it in CoffeeFields.BEANS_VALUES },
                CoffeeFields.CONCENTRATION to { it in CoffeeFields.CONCENTRATIONS },
                CoffeeFields.SUGAR to { it in CoffeeFields.SUGARS },
                CoffeeFields.CREAM to { it in CoffeeFields.CREAMS },
                CoffeeFields.CALORIES to CoffeeFields::isValidCalories,
                CoffeeFields.DRINK to { it in CoffeeFields.LEGACY_DRINKS },
                CoffeeFields.AMOUNT to CoffeeFields::isValidAmount,
                CoffeeFields.UNIT to { it in CoffeeFields.LEGACY_UNITS },
                CoffeeFields.CAFFEINE to { it in CoffeeFields.LEGACY_CAFFEINE_LEVELS }
            )
        ),
        TeaFields.TYPE_CODE to DetailSpec(
            typeCode = TeaFields.TYPE_CODE,
            allowedFields = TeaFields.ALL_KEYS,
            validators = mapOf(
                TeaFields.VARIETY to TeaFields::isValidVariety,
                TeaFields.CUPS to TeaFields::isValidCups,
                TeaFields.AMOUNT to TeaFields::isValidAmount,
                TeaFields.CALORIES to TeaFields::isValidCalories
            )
        ),
        SportFields.TYPE_CODE to DetailSpec(
            typeCode = SportFields.TYPE_CODE,
            allowedFields = setOf(
                SportFields.SPORT,
                SportFields.DURATION,
                SportFields.INTENSITY
            ),
            validators = mapOf(
                // 用完整白名单而不是界面枚举：界面下线的旧类型仍要能从备份恢复。
                SportFields.SPORT to { it in SportFields.ALL_SPORT_VALUES },
                SportFields.DURATION to SportFields::isValidDuration,
                SportFields.INTENSITY to { it in SportFields.INTENSITIES }
            )
        ),
        IntimacyFields.TYPE_CODE to DetailSpec(
            typeCode = IntimacyFields.TYPE_CODE,
            allowedFields = setOf(
                IntimacyFields.METHOD,
                // 旧版三项：只为兼容 ≤0.6.13 的记录与备份，界面不再写入。
                IntimacyFields.PROTECTION,
                IntimacyFields.EXPERIENCE,
                IntimacyFields.DURATION
            ),
            validators = mapOf(
                IntimacyFields.METHOD to IntimacyFields::isValidMethod,
                IntimacyFields.PROTECTION to { it in IntimacyFields.PROTECTION_VALUES },
                IntimacyFields.EXPERIENCE to { it in IntimacyFields.EXPERIENCE_VALUES },
                IntimacyFields.DURATION to IntimacyFields::isValidDuration
            )
        ),
        MenstruationFields.TYPE_CODE to DetailSpec(
            typeCode = MenstruationFields.TYPE_CODE,
            allowedFields = setOf(
                MenstruationFields.FLOW,
                MenstruationFields.PAIN,
                MenstruationFields.SYMPTOMS,
                MenstruationFields.MOOD,
                MenstruationFields.DISCHARGE
            ),
            validators = mapOf(
                MenstruationFields.FLOW to MenstruationFields::isValidFlow,
                MenstruationFields.PAIN to MenstruationFields::isValidPain,
                MenstruationFields.SYMPTOMS to MenstruationFields::isValidSymptoms,
                MenstruationFields.MOOD to MenstruationFields::isValidMood,
                MenstruationFields.DISCHARGE to MenstruationFields::isValidDischarge
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
