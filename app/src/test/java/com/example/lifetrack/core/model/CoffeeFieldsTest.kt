package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
