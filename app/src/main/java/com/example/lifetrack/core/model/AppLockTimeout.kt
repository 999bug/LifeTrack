package com.example.lifetrack.core.model

/** 应用锁的后台超时策略。[minutes] 为 0 表示切到后台立即锁定。 */
enum class AppLockTimeout(val minutes: Int) {
    IMMEDIATE(0),
    ONE_MINUTE(1),
    FIVE_MINUTES(5);

    val millis: Long get() = minutes * 60_000L

    companion object {
        fun fromMinutes(minutes: Int): AppLockTimeout =
            entries.firstOrNull { it.minutes == minutes } ?: IMMEDIATE
    }
}
