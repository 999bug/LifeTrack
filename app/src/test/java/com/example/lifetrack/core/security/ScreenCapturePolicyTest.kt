package com.example.lifetrack.core.security

import com.example.lifetrack.core.database.entity.TrackTypeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCapturePolicyTest {

    private fun type(code: String, enabled: Boolean = true) = TrackTypeEntity(
        code = code,
        name = code,
        iconKey = code,
        category = "activity",
        trackingMode = "EVENT",
        enabled = enabled,
        sortOrder = 0,
        schemaVersion = 1
    )

    @Test
    fun bothOff_allowsScreenCapture() {
        val types = listOf(type("water"), type("intimacy", enabled = false))
        val reasons = ScreenCapturePolicy.reasons(appLockEnabled = false, types)
        assertTrue(reasons.isEmpty())
        assertFalse(ScreenCapturePolicy.isBlocked(appLockEnabled = false, types))
        assertEquals("", ScreenCapturePolicy.describe(reasons))
    }

    @Test
    fun appLockOnly_blocks() {
        val reasons = ScreenCapturePolicy.reasons(true, listOf(type("water")))
        assertEquals(listOf(ScreenCapturePolicy.Reason.APP_LOCK), reasons)
        assertEquals("应用锁已开启", ScreenCapturePolicy.describe(reasons))
    }

    @Test
    fun intimacyModuleOnly_blocksEvenWhenAppLockIsOff() {
        val reasons = ScreenCapturePolicy.reasons(false, listOf(type("intimacy")))
        assertEquals(listOf(ScreenCapturePolicy.Reason.INTIMACY_MODULE), reasons)
        assertEquals("「亲密关系」记录项目已启用", ScreenCapturePolicy.describe(reasons))
    }

    @Test
    fun bothOn_reportsBothReasonsInFixedOrder() {
        val reasons = ScreenCapturePolicy.reasons(true, listOf(type("intimacy")))
        assertEquals(
            listOf(
                ScreenCapturePolicy.Reason.APP_LOCK,
                ScreenCapturePolicy.Reason.INTIMACY_MODULE
            ),
            reasons
        )
        assertEquals(
            "应用锁已开启、「亲密关系」记录项目已启用",
            ScreenCapturePolicy.describe(reasons)
        )
    }

    /** 只有亲密关系是高敏感模块，别把其它启用中的模块也判成遮挡原因。 */
    @Test
    fun otherEnabledModulesDoNotBlock() {
        val types = listOf(type("poop"), type("water"), type("coffee"), type("sport"))
        assertFalse(ScreenCapturePolicy.isBlocked(false, types))
        assertTrue(ScreenCapturePolicy.reasons(false, types).isEmpty())
    }

    @Test
    fun disabledIntimacyTypeDoesNotBlock() {
        assertFalse(
            ScreenCapturePolicy.isBlocked(false, listOf(type("intimacy", enabled = false)))
        )
    }

    /** MainActivity 在数据库 Flow 尚未发射 / 出错时会用空列表兜底，此时只认应用锁。 */
    @Test
    fun emptyTypeListFallsBackToAppLockOnly() {
        assertFalse(ScreenCapturePolicy.isBlocked(false, emptyList()))
        assertEquals(
            listOf(ScreenCapturePolicy.Reason.APP_LOCK),
            ScreenCapturePolicy.reasons(true, emptyList())
        )
    }

    @Test
    fun isBlockedAlwaysAgreesWithReasons() {
        val types = listOf(type("water"), type("intimacy", enabled = false))
        listOf(false, true).forEach { appLock ->
            listOf(types, types + type("intimacy")).forEach { candidate ->
                val reasons = ScreenCapturePolicy.reasons(appLock, candidate)
                assertEquals(reasons.isNotEmpty(), ScreenCapturePolicy.isBlocked(appLock, candidate))
            }
        }
    }
}
