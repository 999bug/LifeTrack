package com.example.lifetrack.core.model

/**
 * 主题模式。
 *
 * [SYSTEM] 跟随设备的深色模式；App 不监听系统变化之外的信号，
 * 也就是切到系统深色时立刻跟随，不需要重启。
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    val label: String
        get() = when (this) {
            LIGHT -> "浅色"
            DARK -> "深色"
            SYSTEM -> "跟随系统"
        }

    companion object {
        /** 从持久化的字符串还原；无法识别时回落到浅色，不抛异常。 */
        fun fromName(value: String?): ThemeMode? =
            entries.firstOrNull { it.name == value }
    }
}
