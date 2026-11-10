package com.example.lifetrack.core.security

import com.example.lifetrack.core.model.AppLockTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun immediate_locksAsSoonAsThereIsBackgroundTime() {
        assertTrue(AppLockPolicy.shouldLock(AppLockTimeout.IMMEDIATE, now - 1, now))
        assertTrue(AppLockPolicy.shouldLock(AppLockTimeout.IMMEDIATE, now, now))
    }

    @Test
    fun oneMinute_waitsForThreshold() {
        assertFalse(AppLockPolicy.shouldLock(AppLockTimeout.ONE_MINUTE, now - 59_999, now))
        assertTrue(AppLockPolicy.shouldLock(AppLockTimeout.ONE_MINUTE, now - 60_000, now))
    }

    @Test
    fun fiveMinutes_waitsForThreshold() {
        assertFalse(AppLockPolicy.shouldLock(AppLockTimeout.FIVE_MINUTES, now - 299_999, now))
        assertTrue(AppLockPolicy.shouldLock(AppLockTimeout.FIVE_MINUTES, now - 300_000, now))
    }

    /** 冷启动没有可信的后台时间，必须锁。 */
    @Test
    fun missingBackgroundTime_locks() {
        assertTrue(AppLockPolicy.shouldLock(AppLockTimeout.FIVE_MINUTES, null, now))
    }

    /** 系统时间往回调时，定时策略不应误判为「已超过」；立即锁定仍应锁。 */
    @Test
    fun clockSkew_doesNotUnlockTimedStrategy() {
        assertFalse(AppLockPolicy.shouldLock(AppLockTimeout.ONE_MINUTE, now + 10_000, now))
        assertTrue(AppLockPolicy.shouldLock(AppLockTimeout.IMMEDIATE, now + 10_000, now))
    }

    @Test
    fun timeoutMinutes_roundTrip() {
        assertEquals(AppLockTimeout.ONE_MINUTE, AppLockTimeout.fromMinutes(1))
        assertEquals(AppLockTimeout.FIVE_MINUTES, AppLockTimeout.fromMinutes(5))
        assertEquals(AppLockTimeout.IMMEDIATE, AppLockTimeout.fromMinutes(0))
        assertEquals(AppLockTimeout.IMMEDIATE, AppLockTimeout.fromMinutes(42))
    }
}
