package com.example.lifetrack.core.backup

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
    fun `registry has builtin water spec and falls back to injected specs`() {
        assertNotNull(DetailSpecRegistry.specFor(WaterFields.TYPE_CODE))
        assertNull(DetailSpecRegistry.specFor("coffee"))
        val coffee = DetailSpec("coffee", allowedFields = setOf("cups"))
        assertEquals(coffee, DetailSpecRegistry.specFor("coffee", mapOf("coffee" to coffee)))
        val override = DetailSpec("water", allowedFields = emptySet())
        assertEquals(override, DetailSpecRegistry.specFor("water", mapOf("water" to override)))
    }

    @Test
    fun `production decode options only allow registered types`() {
        assertEquals(setOf(WaterFields.TYPE_CODE), BackupDecodeOptions.Default.supportedDetailTypes)
        assertEquals(setOf(WaterFields.TYPE_CODE), SUPPORTED_DETAIL_TYPES)
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
}
