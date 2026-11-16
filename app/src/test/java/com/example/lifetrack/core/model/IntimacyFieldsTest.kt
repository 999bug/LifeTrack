package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 亲密关系字段的唯一来源：重构后的「措施」10 选 1 + 旧版三项（仅兼容），枚举与文案都在这里校验。 */
class IntimacyFieldsTest {

    @Test
    fun `duration range is one minute to one day`() {
        assertTrue(IntimacyFields.isValidDuration("1"))
        assertTrue(IntimacyFields.isValidDuration("45"))
        assertTrue(IntimacyFields.isValidDuration("1440"))
        listOf("0", "1441", "-5", "abc", "", "12.5").forEach {
            assertFalse(it, IntimacyFields.isValidDuration(it))
        }
    }

    @Test
    fun `duration label switches to hours above sixty minutes`() {
        assertEquals("30 分钟", IntimacyFields.durationLabel("30"))
        assertEquals("59 分钟", IntimacyFields.durationLabel("59"))
        assertEquals("1 小时", IntimacyFields.durationLabel("60"))
        assertEquals("1 小时 20 分", IntimacyFields.durationLabel("80"))
        assertEquals("24 小时", IntimacyFields.durationLabel("1440"))
        assertEquals("时长未记录", IntimacyFields.durationLabel(""))
        assertEquals("时长未记录", IntimacyFields.durationLabel("abc"))
    }

    @Test
    fun `labels cover every enum value`() {
        assertEquals(
            listOf("是", "否", "不适用"),
            IntimacyFields.PROTECTION_VALUES.map(IntimacyFields::protectionLabel)
        )
        assertEquals(
            listOf("好", "一般", "差"),
            IntimacyFields.EXPERIENCE_VALUES.map(IntimacyFields::experienceLabel)
        )
        assertEquals("未记录", IntimacyFields.protectionLabel(""))
        assertEquals("未记录", IntimacyFields.experienceLabel("unknown"))
    }

    @Test
    fun `field keys stay stable for backup compatibility`() {
        assertEquals("intimacy", IntimacyFields.TYPE_CODE)
        assertEquals("method", IntimacyFields.METHOD)
        // 旧版三项仍要能被恢复，key 不跟着重构改名。
        assertEquals("protection", IntimacyFields.PROTECTION)
        assertEquals("experience", IntimacyFields.EXPERIENCE)
        assertEquals("durationMin", IntimacyFields.DURATION)
    }

    @Test
    fun `method whitelist has ten stable codes in display order`() {
        assertEquals(
            listOf(
                "none", "condom", "withdrawal", "noEjaculation", "emergencyPill",
                "shortPill", "longPill", "iud", "other", "masturbation"
            ),
            IntimacyFields.METHOD_VALUES
        )
        assertEquals(IntimacyFields.METHOD_VALUES.size, IntimacyFields.METHOD_OPTIONS.size)
        IntimacyFields.METHOD_VALUES.forEach { assertTrue(it, IntimacyFields.isValidMethod(it)) }
        assertFalse(IntimacyFields.isValidMethod("pill"))
        assertFalse(IntimacyFields.isValidMethod(""))
    }

    @Test
    fun `method labels cover every option`() {
        assertEquals(
            listOf(
                "无措施", "避孕套", "体外排精", "未射精", "紧急避孕药",
                "短效避孕药", "长效避孕药", "节育环", "其他措施", "自慰"
            ),
            IntimacyFields.METHOD_OPTIONS.map { it.second }
        )
        assertEquals("未记录", IntimacyFields.methodLabel("unknown"))
    }
}
