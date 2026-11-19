package com.example.lifetrack.core.model

import com.example.lifetrack.core.backup.DetailSpecRegistry
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE

data class RecordTypeDefinition(
    val code: String,
    val label: String,
    val sensitive: Boolean,
    val childApp: Boolean = false
)

object RecordTypeRegistry {
    val builtIn: Map<String, RecordTypeDefinition> = listOf(
        RecordTypeDefinition(DEFAULT_POOP_TYPE.code, "排便", sensitive = false),
        RecordTypeDefinition(WaterFields.TYPE_CODE, "饮水", sensitive = false),
        RecordTypeDefinition(CoffeeFields.TYPE_CODE, "咖啡", sensitive = false),
        RecordTypeDefinition(TeaFields.TYPE_CODE, "茶", sensitive = false),
        RecordTypeDefinition(SportFields.TYPE_CODE, "运动", sensitive = false, childApp = true),
        RecordTypeDefinition(IntimacyFields.TYPE_CODE, "亲密关系", sensitive = true, childApp = true),
        RecordTypeDefinition(MenstruationFields.TYPE_CODE, "月经", sensitive = true, childApp = true)
    ).associateBy { it.code }

    fun require(code: String): RecordTypeDefinition =
        builtIn[code] ?: error("Unknown record type: $code")

    fun validate(): List<String> = buildList {
        DetailSpecRegistry.builtIn.keys.forEach { code ->
            if (code !in builtIn) add("missing definition: $code")
        }
        builtIn.values.filter { it.sensitive }.forEach { definition ->
            if (definition.code !in com.example.lifetrack.core.security.SensitiveTypes.HIGH_SENSITIVITY_TYPES) {
                add("sensitive mismatch: ${definition.code}")
            }
        }
    }
}
