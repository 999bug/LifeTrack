package com.example.lifetrack.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 高敏感类型登记：只断言「哪些类型算敏感」与展示顺序。
 *
 * 截屏遮挡（FLAG_SECURE）已在 0.6.x 整体移除，因此不再有 reasons / isBlocked /
 * describe 这类判定需要覆盖；这个登记表现在只服务列表与详情收敛、明文导出文案。
 */
class SensitiveTypesTest {

    @Test
    fun coversIntimacyAndMenstruation() {
        assertEquals(
            setOf("intimacy", "menstruation"),
            SensitiveTypes.HIGH_SENSITIVITY_TYPES
        )
    }

    /** 顺序固定，供「明文导出会包含…」的文案遍历，不受调用方集合顺序影响。 */
    @Test
    fun namesFollowFixedOrder() {
        assertEquals(listOf("亲密关系", "月经"), SensitiveTypes.sensitiveModuleNames)
    }

    /** 别把普通记录类型也标成敏感，否则列表会被无谓地收敛成「已记录」。 */
    @Test
    fun otherTypesAreNotSensitive() {
        listOf("poop", "water", "coffee", "sport").forEach { code ->
            assertTrue("$code 不应算高敏感", code !in SensitiveTypes.HIGH_SENSITIVITY_TYPES)
        }
    }
}
