package com.example.lifetrack.core.update

/**
 * 同步窗口期的重查退避。CI 上传附件通常只需几十秒，越等越久可避免反复打 API；
 * 进程被杀后下次冷启动会重新查一次，不依赖这里的调度存活。
 */
object PendingBackoff {
    private val STEPS = longArrayOf(30_000L, 60_000L, 120_000L, 300_000L)

    /** @param attempt 已经进入 Pending 的次数，从 0 开始。 */
    fun delayMillis(attempt: Int): Long = STEPS[attempt.coerceIn(0, STEPS.lastIndex)]
}
