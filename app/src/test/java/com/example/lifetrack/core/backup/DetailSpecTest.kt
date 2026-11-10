package com.example.lifetrack.core.backup

import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.SportFields
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
    fun `registry has builtin water coffee sport and intimacy specs and falls back to injected specs`() {
        assertNotNull(DetailSpecRegistry.specFor(WaterFields.TYPE_CODE))
        assertNotNull(DetailSpecRegistry.specFor(CoffeeFields.TYPE_CODE))
        assertNotNull(DetailSpecRegistry.specFor(SportFields.TYPE_CODE))
        assertNotNull(DetailSpecRegistry.specFor(IntimacyFields.TYPE_CODE))
        assertNull(DetailSpecRegistry.specFor("mood"))
        val mood = DetailSpec("mood", allowedFields = setOf("score"), validators = mapOf("score" to { it == "3" }))
        assertEquals(mood, DetailSpecRegistry.specFor("mood", mapOf("mood" to mood)))
        val override = DetailSpec("water", allowedFields = emptySet())
        assertEquals(override, DetailSpecRegistry.specFor("water", mapOf("water" to override)))
    }

    @Test
    fun `production decode options only allow registered types`() {
        val expected = setOf(
            WaterFields.TYPE_CODE,
            CoffeeFields.TYPE_CODE,
            SportFields.TYPE_CODE,
            IntimacyFields.TYPE_CODE
        )
        assertEquals(expected, BackupDecodeOptions.Default.supportedDetailTypes)
        assertEquals(expected, SUPPORTED_DETAIL_TYPES)
        assertTrue(BackupDecodeOptions.Default.detailSpecs.isEmpty())
    }

    @Test
    fun `every builtin spec validates all of its declared fields`() {
        DetailSpecRegistry.builtIn.values.forEach { spec ->
            assertTrue(
                "${spec.typeCode} 缺少约束",
                spec.allowedFields.isNotEmpty()
            )
            assertEquals(
                "${spec.typeCode} 的字段与校验器必须一一对应",
                spec.allowedFields,
                spec.validators.keys
            )
        }
    }

    @Test
    fun `spec rejects both unknown fields and invalid values`() {
        val spec = DetailSpecRegistry.specFor(WaterFields.TYPE_CODE)!!
        assertTrue(spec.isFieldsValid(mapOf(WaterFields.AMOUNT to "250")))
        assertFalse(spec.isFieldsValid(mapOf(WaterFields.AMOUNT to "250", "caffeine" to "yes")))
        assertFalse(spec.isFieldsValid(mapOf(WaterFields.AMOUNT to "99999")))
    }

    @Test
    fun `declaring a field without a validator fails fast`() {
        val error = runCatching {
            DetailSpec("mood", allowedFields = setOf("score"))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
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

    @Test
    fun `sport spec enforces sport enum and duration range`() {
        val spec = DetailSpecRegistry.specFor(SportFields.TYPE_CODE)!!
        assertEquals(
            setOf(SportFields.SPORT, SportFields.DURATION, SportFields.INTENSITY),
            spec.allowedFields
        )
        assertTrue(spec.validators[SportFields.DURATION]!!("45"))
        assertTrue(spec.validators[SportFields.DURATION]!!("1440"))
        assertFalse(spec.validators[SportFields.DURATION]!!("0"))
        assertFalse(spec.validators[SportFields.DURATION]!!("1441"))
        assertFalse(spec.validators[SportFields.DURATION]!!("abc"))
        SportFields.SPORTS.forEach { assertTrue(it, spec.validators[SportFields.SPORT]!!(it)) }
        assertFalse(spec.validators[SportFields.SPORT]!!("ski"))
        SportFields.INTENSITIES.forEach { assertTrue(it, spec.validators[SportFields.INTENSITY]!!(it)) }
        assertFalse(spec.validators[SportFields.INTENSITY]!!("extreme"))
    }

    @Test
    fun `intimacy spec enforces enums and optional duration range`() {
        val spec = DetailSpecRegistry.specFor(IntimacyFields.TYPE_CODE)!!
        assertEquals(
            setOf(IntimacyFields.PROTECTION, IntimacyFields.EXPERIENCE, IntimacyFields.DURATION),
            spec.allowedFields
        )
        assertTrue(spec.isFieldsValid(emptyMap()))
        assertTrue(spec.isFieldsValid(mapOf(IntimacyFields.PROTECTION to "na")))
        assertTrue(spec.validators[IntimacyFields.DURATION]!!("1440"))
        assertFalse(spec.validators[IntimacyFields.DURATION]!!("1441"))
        assertFalse(spec.validators[IntimacyFields.DURATION]!!("abc"))
        IntimacyFields.PROTECTION_VALUES.forEach {
            assertTrue(it, spec.validators[IntimacyFields.PROTECTION]!!(it))
        }
        assertFalse(spec.validators[IntimacyFields.PROTECTION]!!("maybe"))
        IntimacyFields.EXPERIENCE_VALUES.forEach {
            assertTrue(it, spec.validators[IntimacyFields.EXPERIENCE]!!(it))
        }
        assertFalse(spec.validators[IntimacyFields.EXPERIENCE]!!("great"))
        assertFalse(spec.isFieldsValid(mapOf("partnerName" to "someone")))
    }
}
