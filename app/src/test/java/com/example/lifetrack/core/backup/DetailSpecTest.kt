package com.example.lifetrack.core.backup

import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.WaterFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailSpecTest {

    @Test
    fun `structural validation accepts well formed fields`() {
        assertTrue(DetailLimits.isStructuralValid(emptyMap()))
        assertTrue(DetailLimits.isStructuralValid(mapOf("amountMl" to "250", "unit_2" to "ml")))
    }

    @Test
    fun `structural validation rejects malformed keys`() {
        assertFalse(DetailLimits.isStructuralValid(mapOf("AmountMl" to "250")))
        assertFalse(DetailLimits.isStructuralValid(mapOf("" to "250")))
        assertFalse(DetailLimits.isStructuralValid(mapOf("2amount" to "250")))
        assertFalse(DetailLimits.isStructuralValid(mapOf("a-b" to "250")))
        assertFalse(DetailLimits.isStructuralValid(mapOf("x".repeat(33) to "1")))
    }

    @Test
    fun `structural validation enforces size limits`() {
        assertFalse(DetailLimits.isStructuralValid(mapOf("amountMl" to "x".repeat(257))))
        assertFalse(
            DetailLimits.isStructuralValid(
                (1..DetailLimits.MAX_FIELDS + 1).associate { "k$it" to "1" }
            )
        )
        assertTrue(
            DetailLimits.isStructuralValid(
                (1..DetailLimits.MAX_FIELDS).associate { "k$it" to "1" }
            )
        )
    }

    @Test
    fun `registry has builtin water and coffee specs and falls back to injected specs`() {
        assertNotNull(DetailSpecRegistry.specFor(WaterFields.TYPE_CODE))
        assertNotNull(DetailSpecRegistry.specFor(CoffeeFields.TYPE_CODE))
        assertNull(DetailSpecRegistry.specFor("sport"))
        val sport = DetailSpec("sport", allowedFields = setOf("minutes"))
        assertEquals(sport, DetailSpecRegistry.specFor("sport", mapOf("sport" to sport)))
        val override = DetailSpec("water", allowedFields = emptySet())
        assertEquals(override, DetailSpecRegistry.specFor("water", mapOf("water" to override)))
    }

    @Test
    fun `production decode options only allow registered types`() {
        assertEquals(
            setOf(WaterFields.TYPE_CODE, CoffeeFields.TYPE_CODE),
            BackupDecodeOptions.Default.supportedDetailTypes
        )
        assertEquals(
            setOf(WaterFields.TYPE_CODE, CoffeeFields.TYPE_CODE),
            SUPPORTED_DETAIL_TYPES
        )
        assertTrue(BackupDecodeOptions.Default.detailSpecs.isEmpty())
    }

    @Test
    fun `water spec enforces amount range and unit enum`() {
        val spec = DetailSpecRegistry.specFor(WaterFields.TYPE_CODE)!!
        assertTrue(spec.validators[WaterFields.AMOUNT]!!("250"))
        assertFalse(spec.validators[WaterFields.AMOUNT]!!("0"))
        assertFalse(spec.validators[WaterFields.AMOUNT]!!("5001"))
        assertFalse(spec.validators[WaterFields.AMOUNT]!!("abc"))
        assertTrue(spec.validators[WaterFields.UNIT]!!("ml"))
        assertTrue(spec.validators[WaterFields.UNIT]!!("cup"))
        assertFalse(spec.validators[WaterFields.UNIT]!!("litre"))
    }

    @Test
    fun `coffee spec enforces enums and amount range`() {
        val spec = DetailSpecRegistry.specFor(CoffeeFields.TYPE_CODE)!!
        assertEquals(
            setOf(
                CoffeeFields.DRINK,
                CoffeeFields.AMOUNT,
                CoffeeFields.UNIT,
                CoffeeFields.CAFFEINE
            ),
            spec.allowedFields
        )
        assertTrue(spec.validators[CoffeeFields.AMOUNT]!!("300"))
        assertFalse(spec.validators[CoffeeFields.AMOUNT]!!("0"))
        assertFalse(spec.validators[CoffeeFields.AMOUNT]!!("2001"))
        assertFalse(spec.validators[CoffeeFields.AMOUNT]!!("abc"))
        assertTrue(spec.validators[CoffeeFields.DRINK]!!("tea"))
        assertFalse(spec.validators[CoffeeFields.DRINK]!!("juice"))
        assertTrue(spec.validators[CoffeeFields.CAFFEINE]!!("low"))
        assertFalse(spec.validators[CoffeeFields.CAFFEINE]!!("high"))
        assertTrue(spec.validators[CoffeeFields.UNIT]!!("cup"))
        assertFalse(spec.validators[CoffeeFields.UNIT]!!("litre"))
    }
}
