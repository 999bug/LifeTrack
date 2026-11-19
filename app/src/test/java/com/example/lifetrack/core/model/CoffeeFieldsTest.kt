package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A7：编辑器输入校验与备份规格共用同一份字段常量，这里锁定边界与展示标签。 */
class CoffeeFieldsTest {

    @Test
    fun `amount accepts inclusive range boundaries`() {
        assertTrue(CoffeeFields.isValidAmount("1"))
        assertTrue(CoffeeFields.isValidAmount("300"))
        assertTrue(CoffeeFields.isValidAmount("2000"))
    }

    @Test
    fun `amount rejects zero overflow and non numeric input`() {
        listOf("0", "2001", "99999", "-1", "abc", "", " 300 ").forEach {
            assertFalse(it, CoffeeFields.isValidAmount(it))
        }
    }

    @Test
    fun `drink labels cover coffee and tea`() {
        assertEquals("咖啡", CoffeeFields.drinkLabel("coffee"))
        assertEquals("茶", CoffeeFields.drinkLabel("tea"))
        assertEquals("咖啡", CoffeeFields.drinkLabel("unknown"))
    }

    @Test
    fun `unit and caffeine labels are deterministic`() {
        assertEquals("ml", CoffeeFields.unitLabel("ml"))
        assertEquals("杯", CoffeeFields.unitLabel("cup"))
        assertEquals("含咖啡因", CoffeeFields.caffeineLabel("yes"))
        assertEquals("低因", CoffeeFields.caffeineLabel("low"))
        assertEquals("无咖啡因", CoffeeFields.caffeineLabel("no"))
    }

    @Test
    fun `enums are distinct and non empty`() {
        assertEquals(listOf("coffee", "tea"), CoffeeFields.DRINKS)
        assertEquals(listOf("ml", "cup"), CoffeeFields.UNITS)
        assertEquals(listOf("yes", "low", "no"), CoffeeFields.CAFFEINE_LEVELS)
    }

    @Test
    fun `new field validators hold their ranges`() {
        assertTrue(CoffeeFields.isValidCalories("0"))
        assertTrue(CoffeeFields.isValidCalories("120"))
        assertTrue(CoffeeFields.isValidCalories("3000"))
        listOf("3001", "-1", "abc", "").forEach { assertFalse(it, CoffeeFields.isValidCalories(it)) }
        assertTrue(CoffeeFields.isValidVariety("橙C美式"))
        assertFalse(CoffeeFields.isValidVariety(" "))
        assertFalse(CoffeeFields.isValidVariety("x".repeat(CoffeeFields.MAX_VARIETY_LENGTH + 1)))
    }

    @Test
    fun `cup sizes map to milliliters and labels`() {
        assertEquals(350, CoffeeFields.SIZE_ML["medium"])
        assertEquals(450, CoffeeFields.SIZE_ML["large"])
        assertEquals(596, CoffeeFields.SIZE_ML["xlarge"])
        assertEquals("超大杯", CoffeeFields.sizeLabel("xlarge"))
        assertEquals("意式拼配", CoffeeFields.beansLabel("espresso"))
        assertEquals("不另外加糖", CoffeeFields.sugarLabel("none"))
    }

    @Test
    fun `preset varieties have default calories and custom ones do not`() {
        assertEquals(95, CoffeeFields.defaultCalories("橙C美式"))
        assertEquals(15, CoffeeFields.defaultCalories("经典美式"))
        assertNull(CoffeeFields.defaultCalories("我的特调"))
    }
}
