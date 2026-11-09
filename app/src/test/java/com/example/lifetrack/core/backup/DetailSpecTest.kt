package com.example.lifetrack.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `registry falls back to injected specs`() {
        assertNull(DetailSpecRegistry.specFor("water"))
        val spec = DetailSpec("water", allowedFields = setOf("amountMl"))
        assertEquals(spec, DetailSpecRegistry.specFor("water", mapOf("water" to spec)))
    }

    @Test
    fun `production decode options support no generic type`() {
        assertTrue(BackupDecodeOptions.Default.supportedDetailTypes.isEmpty())
        assertTrue(BackupDecodeOptions.Default.detailSpecs.isEmpty())
    }
}
