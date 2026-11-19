package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.7 类型拆分：旧茶记录（coffee 类型 + drinkType=tea）的迁移口径。 */
class TeaMigrationTest {

    @Test
    fun `recognizes only legacy tea among coffee records`() {
        assertTrue(TeaMigration.isLegacyTea(mapOf("drinkType" to "tea", "amountMl" to "300")))
        assertFalse(TeaMigration.isLegacyTea(mapOf("drinkType" to "coffee", "amountMl" to "300")))
        assertFalse(TeaMigration.isLegacyTea(mapOf("amountMl" to "300")))
        assertFalse(TeaMigration.isLegacyTea(emptyMap()))
    }

    @Test
    fun `non legacy records are not converted`() {
        assertNull(TeaMigration.convertLegacyTea(mapOf("drinkType" to "coffee", "amountMl" to "300")))
        assertNull(TeaMigration.convertLegacyTea(emptyMap()))
    }

    @Test
    fun `ml based legacy tea keeps amount and drops old keys`() {
        val converted = TeaMigration.convertLegacyTea(
            mapOf(
                "drinkType" to "tea",
                "amountMl" to "300",
                "unit" to "ml",
                "caffeine" to "no"
            )
        )!!
        assertEquals(mapOf("amountMl" to "300"), converted)
        // 旧键全部被丢掉：tea 规格不认识它们。
        assertFalse(converted.containsKey("drinkType"))
        assertFalse(converted.containsKey("unit"))
        assertFalse(converted.containsKey("caffeine"))
    }

    @Test
    fun `cup based legacy tea converts cups and estimates ml`() {
        val converted = TeaMigration.convertLegacyTea(
            mapOf("drinkType" to "tea", "amountMl" to "2", "unit" to "cup")
        )!!
        assertEquals("2", converted[TeaFields.CUPS])
        assertEquals("500", converted[TeaFields.AMOUNT])
    }

    @Test
    fun `converted fields pass tea detail spec`() {
        val legacy = mapOf("drinkType" to "tea", "amountMl" to "2", "unit" to "cup", "caffeine" to "low")
        val converted = TeaMigration.convertLegacyTea(legacy)!!
        val spec = com.example.lifetrack.core.backup.DetailSpecRegistry.specFor(TeaFields.TYPE_CODE)!!
        assertTrue(spec.isFieldsValid(converted))
    }
}
