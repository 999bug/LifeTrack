package com.example.lifetrack.core.security

import com.example.lifetrack.core.model.AppLockTimeout

/**
 * 应用锁的判定规则，纯函数实现，便于单测固定边界。
 *
 * 冷启动不在这里判：进程被杀后 [AppLockGate] 直接把状态置为锁定，
 * 因为此时没有任何可信的「上次退到后台」时间。
 */
object AppLockPolicy {

    /**
     * @param lastBackgroundAtMillis 上次 ON_STOP 的时间；null 表示没有可用的后台时间
     * @return true 表示应当要求解锁
     */
    fun shouldLock(
        timeout: AppLockTimeout,
        lastBackgroundAtMillis: Long?,
        nowMillis: Long
    ): Boolean {
        if (lastBackgroundAtMillis == null) return true
        val elapsed = nowMillis - lastBackgroundAtMillis
        if (elapsed <= 0) return timeout == AppLockTimeout.IMMEDIATE
        return elapsed >= timeout.millis
    }
}
