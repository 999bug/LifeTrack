package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A10：亲密关系字段的唯一来源，三项均可空，范围、枚举与展示文案都在这里校验。 */
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
        assertEquals("protection", IntimacyFields.PROTECTION)
        assertEquals("experience", IntimacyFields.EXPERIENCE)
        assertEquals("durationMin", IntimacyFields.DURATION)
    }
}
