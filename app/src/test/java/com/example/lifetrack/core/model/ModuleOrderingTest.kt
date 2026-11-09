package com.example.lifetrack.core.model

import com.example.lifetrack.core.database.entity.TrackTypeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ModuleOrderingTest {

    private fun type(code: String, order: Int, enabled: Boolean = true) = TrackTypeEntity(
        code = code,
        name = code,
        iconKey = code,
        category = "body",
        trackingMode = "EVENT",
        enabled = enabled,
        sortOrder = order,
        schemaVersion = 1
    )

    private val types = listOf(type("poop", 10), type("drink", 20), type("coffee", 30))

    @Test
    fun `move down swaps neighbours and renumbers`() {
        val moved = ModuleOrdering.move(types, "poop", 1)

        assertEquals(listOf("drink", "poop", "coffee"), moved.map { it.code })
        assertEquals(listOf(10, 20, 30), moved.map { it.sortOrder })
    }

    @Test
    fun `move up swaps neighbours and renumbers`() {
        val moved = ModuleOrdering.move(types, "coffee", -1)

        assertEquals(listOf("poop", "coffee", "drink"), moved.map { it.code })
        assertEquals(listOf(10, 20, 30), moved.map { it.sortOrder })
    }

    @Test
    fun `boundary moves are no-ops`() {
        assertSame(types, ModuleOrdering.move(types, "poop", -1))
        assertSame(types, ModuleOrdering.move(types, "coffee", 1))
        assertSame(types, ModuleOrdering.move(types, "poop", 0))
        assertSame(types, ModuleOrdering.move(types, "missing", 1))
    }

    @Test
    fun `renumbering fixes duplicated sort orders`() {
        val messy = listOf(type("poop", 5), type("drink", 5), type("coffee", 5))

        val moved = ModuleOrdering.move(messy, "poop", 1)

        assertEquals(listOf(10, 20, 30), moved.map { it.sortOrder })
    }

    @Test
    fun `other fields are preserved`() {
        val withDisabled = listOf(type("poop", 10), type("drink", 20, enabled = false))

        val moved = ModuleOrdering.move(withDisabled, "drink", -1)

        assertEquals(false, moved.first().enabled)
        assertEquals("EVENT", moved.first().trackingMode)
        assertEquals(1, moved.first().schemaVersion)
    }
}
