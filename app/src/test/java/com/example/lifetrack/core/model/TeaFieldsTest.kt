package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeaFieldsTest {

    @Test
    fun `cups amount and calories enforce ranges`() {
        assertTrue(TeaFields.isValidCups("1"))
        assertTrue(TeaFields.isValidCups("20"))
        assertFalse(TeaFields.isValidCups("0"))
        assertFalse(TeaFields.isValidCups("21"))
        assertTrue(TeaFields.isValidAmount("300"))
        assertFalse(TeaFields.isValidAmount("0"))
        assertFalse(TeaFields.isValidAmount("5001"))
        assertTrue(TeaFields.isValidCalories("5"))
        assertFalse(TeaFields.isValidCalories("3001"))
    }

    @Test
    fun `variety is flexible but bounded`() {
        assertTrue(TeaFields.isValidVariety("茉莉花茶"))
        assertTrue(TeaFields.isValidVariety("白牡丹"))
        assertFalse(TeaFields.isValidVariety(" "))
        assertFalse(TeaFields.isValidVariety("x".repeat(TeaFields.MAX_VARIETY_LENGTH + 1)))
    }

    @Test
    fun `preset teas have low default calories and custom ones do not`() {
        assertEquals(5, TeaFields.defaultCalories("绿茶"))
        assertNull(TeaFields.defaultCalories("自泡水果茶"))
    }
}
