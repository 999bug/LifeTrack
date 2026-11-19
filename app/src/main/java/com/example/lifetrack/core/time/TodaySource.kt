package com.example.lifetrack.core.time

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内唯一的业务日期源。
 *
 * [clock] 与 [Clock.zone] 一旦注入，日期计算不再读取系统时间；测试可替换成可变时钟，
 * 生产环境默认使用系统时区。界面只观察 [today]，需要主动校正时调用 [refresh]。
 */
class TodaySource(
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val _today = MutableStateFlow(currentDate())

    val today: StateFlow<LocalDate> = _today.asStateFlow()

    /** 读取当前业务日期，不改变已发布的 [today]。 */
    fun currentDate(): LocalDate = LocalDate.now(clock)

    /** 重新读取当前日期；跨午夜或回到前台后调用。 */
    fun refresh(): LocalDate {
        val current = currentDate()
        if (_today.value != current) {
            _today.value = current
        }
        return current
    }

    /** 距离下一个本地午夜还有多少毫秒，至少返回 1 毫秒避免忙循环。 */
    fun millisUntilNextDay(): Long {
        val nextMidnight = currentDate()
            .plusDays(1)
            .atStartOfDay(clock.zone)
            .toInstant()
        return Duration.between(clock.instant(), nextMidnight)
            .toMillis()
            .coerceAtLeast(1L)
    }
}
