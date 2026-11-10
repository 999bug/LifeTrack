package com.example.lifetrack.core.security

/**
 * 口令失败重试节流：前两次失败不等待，之后按 2s → 4s → 8s → 16s → 30s 递增。
 *
 * 目的不是防住有备而来的离线爆破（备份文件本身可以被无限次尝试），
 * 而是拦住「边猜边点」的误操作与脚本化点击，给用户留出确认口令的时间。
 * 真正的抗爆破能力来自 PBKDF2 的迭代次数，不是这段 UI 延时。
 */
object RetryBackoff {

    const val FREE_ATTEMPTS = 2
    const val BASE_DELAY_MILLIS = 2_000L
    const val MAX_DELAY_MILLIS = 30_000L

    /** [failedAttempts] 是累计失败次数（含本次）。返回下一次尝试前必须等待的毫秒数。 */
    fun delayMillisAfter(failedAttempts: Int): Long {
        if (failedAttempts <= FREE_ATTEMPTS) return 0
        val exponent = failedAttempts - FREE_ATTEMPTS - 1
        if (exponent >= 5) return MAX_DELAY_MILLIS
        return minOf(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS shl exponent)
    }

    /** 把毫秒换算成 UI 需要显示的整秒，向上取整，避免显示「0 秒」却仍然点不动。 */
    fun secondsAfter(failedAttempts: Int): Int {
        val millis = delayMillisAfter(failedAttempts)
        return if (millis <= 0) 0 else ((millis + 999) / 1000).toInt()
    }
}
