package com.example.lifetrack.feature.poop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BristolCatalogTest {

    @Test
    fun `catalog covers 1 to 7 in order`() {
        assertEquals((1..7).toList(), BristolCatalog.all.map { it.number })
        assertEquals(BristolCatalog.MIN, BristolCatalog.all.first().number)
        assertEquals(BristolCatalog.MAX, BristolCatalog.all.last().number)
    }

    @Test
    fun `every type has readable label and description`() {
        BristolCatalog.all.forEach {
            assertTrue("missing short label for type ${it.number}", it.shortLabel.isNotBlank())
            assertTrue("missing description for type ${it.number}", it.description.isNotBlank())
            assertTrue("missing group for type ${it.number}", it.group.isNotBlank())
        }
    }

    @Test
    fun `groups follow the water axis`() {
        assertEquals(
            listOf("偏干端", "偏干端", "中间段", "中间段", "偏稀端", "偏稀端", "偏稀端"),
            BristolCatalog.all.map { it.group }
        )
    }

    @Test
    fun `group ranges cover 1 to 7 without overlap`() {
        val numbers = BristolCatalog.groups.flatMap { it.second.toList() }
        assertEquals((1..7).toList(), numbers)
        assertEquals(numbers.size, numbers.distinct().size)
    }

    @Test
    fun `of returns the matching type`() {
        (1..7).forEach { number ->
            assertEquals(number, BristolCatalog.of(number)?.number)
        }
    }

    @Test
    fun `of rejects out of range values`() {
        listOf(Int.MIN_VALUE, -1, 0, 8, 99, Int.MAX_VALUE).forEach {
            assertNull("out-of-range value $it must not map to a shape", BristolCatalog.of(it))
        }
    }
}
