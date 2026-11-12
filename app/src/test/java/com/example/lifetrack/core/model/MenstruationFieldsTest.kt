package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A9：月经字段的唯一来源，五项均可空，枚举、症状白名单与编码都在这里校验。 */
class MenstruationFieldsTest {

    @Test
    fun `field keys stay stable for backup compatibility`() {
        assertEquals("menstruation", MenstruationFields.TYPE_CODE)
        assertEquals("flow", MenstruationFields.FLOW)
        assertEquals("pain", MenstruationFields.PAIN)
        assertEquals("symptoms", MenstruationFields.SYMPTOMS)
        assertEquals("mood", MenstruationFields.MOOD)
        assertEquals("discharge", MenstruationFields.DISCHARGE)
    }

    @Test
    fun `enum validators accept every declared value and reject others`() {
        MenstruationFields.FLOW_VALUES.forEach { assertTrue(it, MenstruationFields.isValidFlow(it)) }
        MenstruationFields.PAIN_VALUES.forEach { assertTrue(it, MenstruationFields.isValidPain(it)) }
        MenstruationFields.MOOD_VALUES.forEach { assertTrue(it, MenstruationFields.isValidMood(it)) }
        MenstruationFields.DISCHARGE_VALUES.forEach { assertTrue(it, MenstruationFields.isValidDischarge(it)) }
        listOf("", "unknown", "Heavy", "none ").forEach {
            assertFalse(it, MenstruationFields.isValidFlow(it))
            assertFalse(it, MenstruationFields.isValidPain(it))
            assertFalse(it, MenstruationFields.isValidMood(it))
            assertFalse(it, MenstruationFields.isValidDischarge(it))
        }
    }

    @Test
    fun `labels cover every enum value`() {
        assertEquals(
            listOf("点滴", "少", "中", "多"),
            MenstruationFields.FLOW_VALUES.map(MenstruationFields::flowLabel)
        )
        assertEquals(
            listOf("无", "轻", "中", "重"),
            MenstruationFields.PAIN_VALUES.map(MenstruationFields::painLabel)
        )
        assertEquals(
            listOf("平静", "愉快", "敏感", "低落", "易怒"),
            MenstruationFields.MOOD_VALUES.map(MenstruationFields::moodLabel)
        )
        assertEquals(
            listOf("干燥", "黏稠", "乳白", "水样", "蛋清"),
            MenstruationFields.DISCHARGE_VALUES.map(MenstruationFields::dischargeLabel)
        )
    }

    @Test
    fun `unknown or empty enum values fall back to not recorded`() {
        assertEquals("未记录", MenstruationFields.flowLabel(""))
        assertEquals("未记录", MenstruationFields.painLabel("x"))
        assertEquals("未记录", MenstruationFields.moodLabel("angry"))
        assertEquals("未记录", MenstruationFields.dischargeLabel("watery "))
    }

    @Test
    fun `symptom whitelist has twelve stable codes with chinese labels`() {
        assertEquals(12, MenstruationFields.SYMPTOM_CODES.size)
        assertEquals(12, MenstruationFields.SYMPTOM_CODES.toSet().size)
        assertEquals(
            listOf(
                "腹痛", "腰酸", "头痛", "乳房胀痛", "腹胀", "恶心",
                "乏力", "失眠", "情绪波动", "食欲变化", "痤疮", "头晕"
            ),
            MenstruationFields.SYMPTOM_CODES.map(MenstruationFields::symptomLabel)
        )
        assertEquals("未知症状", MenstruationFields.symptomLabel("fever"))
    }

    @Test
    fun `symptoms encode and decode round trip`() {
        val codes = listOf("cramps", "fatigue", "insomnia")
        val encoded = MenstruationFields.encodeSymptoms(codes)
        assertEquals("cramps|fatigue|insomnia", encoded)
        assertEquals(codes, MenstruationFields.decodeSymptoms(encoded))
        assertTrue(MenstruationFields.isValidSymptoms(encoded))
        assertEquals(emptyList<String>(), MenstruationFields.decodeSymptoms(""))
        assertEquals("cramps", MenstruationFields.encodeSymptoms(listOf("cramps")))
    }

    @Test
    fun `symptoms validator rejects empty duplicate illegal and over long values`() {
        assertFalse(MenstruationFields.isValidSymptoms(""))
        assertFalse(MenstruationFields.isValidSymptoms("cramps|cramps"))
        assertFalse(MenstruationFields.isValidSymptoms("cramps|fever"))
        assertFalse(MenstruationFields.isValidSymptoms("cramps|"))
        assertFalse(MenstruationFields.isValidSymptoms("|cramps"))
        assertFalse(MenstruationFields.isValidSymptoms("a".repeat(257)))
        assertTrue(MenstruationFields.isValidSymptoms("cramps"))
        assertTrue(MenstruationFields.isValidSymptoms("cramps|backache|headache"))
    }

    @Test
    fun `symptoms validator enforces the eight item cap`() {
        val eight = MenstruationFields.SYMPTOM_CODES.take(8)
        val nine = MenstruationFields.SYMPTOM_CODES.take(9)
        assertEquals(8, MenstruationFields.MAX_SYMPTOMS)
        assertTrue(MenstruationFields.isValidSymptoms(MenstruationFields.encodeSymptoms(eight)))
        assertFalse(MenstruationFields.isValidSymptoms(MenstruationFields.encodeSymptoms(nine)))
    }

    @Test
    fun `eight longest symptoms still fit the value length limit`() {
        val longestEight = MenstruationFields.SYMPTOM_CODES.sortedByDescending { it.length }.take(8)
        val encoded = MenstruationFields.encodeSymptoms(longestEight)
        assertTrue(encoded.length <= MenstruationFields.MAX_SYMPTOMS_LENGTH)
        assertTrue(MenstruationFields.isValidSymptoms(encoded))
    }
}