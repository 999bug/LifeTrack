package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A8：运动字段的唯一来源，范围、枚举与展示文案都在这里校验。 */
class SportFieldsTest {

    @Test
    fun `duration range is one minute to one day`() {
        assertTrue(SportFields.isValidDuration("1"))
        assertTrue(SportFields.isValidDuration("45"))
        assertTrue(SportFields.isValidDuration("1440"))
        listOf("0", "1441", "-5", "abc", "", "12.5").forEach {
            assertFalse(it, SportFields.isValidDuration(it))
        }
    }

    @Test
    fun `duration label switches to hours above sixty minutes`() {
        assertEquals("30 分钟", SportFields.durationLabel("30"))
        assertEquals("59 分钟", SportFields.durationLabel("59"))
        assertEquals("1 小时", SportFields.durationLabel("60"))
        assertEquals("1 小时 20 分", SportFields.durationLabel("80"))
        assertEquals("24 小时", SportFields.durationLabel("1440"))
        assertEquals("时长未记录", SportFields.durationLabel(""))
        assertEquals("时长未记录", SportFields.durationLabel("abc"))
    }

    @Test
    fun `labels cover every enum value`() {
        assertEquals(listOf("步行", "跑步", "骑行", "游泳", "力量", "其他"), SportFields.SPORTS.map(SportFields::sportLabel))
        assertEquals("低强度", SportFields.intensityLabel("low"))
        assertEquals("中等强度", SportFields.intensityLabel("medium"))
        assertEquals("高强度", SportFields.intensityLabel("high"))
        assertEquals("强度未记录", SportFields.intensityLabel(""))
        assertEquals("运动", SportFields.sportLabel("unknown"))
    }
}
