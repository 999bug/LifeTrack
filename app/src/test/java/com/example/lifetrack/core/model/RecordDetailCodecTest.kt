package com.example.lifetrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDetailCodecTest {

    @Test
    fun `round trip keeps every field`() {
        val fields = mapOf("amountMl" to "250", "unit" to "ml", "noteKey" to "a=b")
        assertEquals(fields, RecordDetailCodec.decode(RecordDetailCodec.encode(fields)))
    }

    @Test
    fun `empty map round trip`() {
        val payload = RecordDetailCodec.encode(emptyMap())
        assertEquals("{}", payload)
        assertEquals(emptyMap<String, String>(), RecordDetailCodec.decode(payload))
    }

    @Test
    fun `escapes special characters`() {
        val fields = mapOf("unit" to "ml\"cup", "noteKey" to "中文\n换行")
        assertEquals(fields, RecordDetailCodec.decode(RecordDetailCodec.encode(fields)))
    }

    @Test
    fun `decode returns null for a broken payload`() {
        assertNull(RecordDetailCodec.decode("not json"))
        assertNull(RecordDetailCodec.decode("""{"amountMl":250}"""))
        assertNull(RecordDetailCodec.decode(""))
    }

    @Test
    fun `encode produces a json object not an array`() {
        val payload = RecordDetailCodec.encode(mapOf("amountMl" to "250"))
        assertTrue(payload.startsWith("{"))
        assertTrue(payload.endsWith("}"))
    }
}
