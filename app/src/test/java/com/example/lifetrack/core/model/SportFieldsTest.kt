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
        assertEquals("0 分钟", SportFields.minutesLabel(0))
        assertEquals("1 小时 20 分", SportFields.minutesLabel(80))
    }

    @Test
    fun `labels cover every enum value`() {
        assertEquals(listOf("瑜伽", "骑行", "跑步"), SportFields.SPORTS.map(SportFields::sportLabel))
        assertEquals("低强度", SportFields.intensityLabel("low"))
        assertEquals("中等强度", SportFields.intensityLabel("medium"))
        assertEquals("高强度", SportFields.intensityLabel("high"))
        assertEquals("强度未记录", SportFields.intensityLabel(""))
        assertEquals("运动", SportFields.sportLabel("unknown"))
    }

    /**
     * 界面只留三类，但旧值的标签必须还在：删掉任一分支，历史记录会一起退化成「运动」。
     * 这条测试是「删除类型」与「保留兼容」之间唯一的护栏。
     */
    @Test
    fun `legacy types keep their labels`() {
        assertEquals(listOf("步行", "游泳", "力量", "其他"), SportFields.LEGACY_SPORTS.map(SportFields::sportLabel))
        assertEquals(
            listOf("yoga", "ride", "run", "walk", "swim", "strength", "other"),
            SportFields.ALL_SPORT_VALUES
        )
        SportFields.LEGACY_SPORTS.forEach { value ->
            assertTrue(value, SportFields.isLegacy(value))
            assertFalse(value, SportFields.isSelectable(value))
        }
        SportFields.SPORTS.forEach { value ->
            assertTrue(value, SportFields.isSelectable(value))
            assertFalse(value, SportFields.isLegacy(value))
        }
    }
}
