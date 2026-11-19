package com.example.lifetrack.core.ui

import com.example.lifetrack.core.backup.DetailSpecRegistry
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.core.model.RecordTypeRegistry
import com.example.lifetrack.core.security.SensitiveTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordTypeUiRegistryTest {
    @Test
    fun everyBuiltInTypeHasUiContribution() {
        assertEquals(RecordTypeRegistry.builtIn.keys, RecordTypeUiRegistry.builtIn.keys)
    }

    @Test
    fun everyBuiltInTypeHasFieldSpecAndSensitivity() {
        val codesWithSpecs = DetailSpecRegistry.builtIn.keys + PoopFields.TYPE_CODE
        assertEquals(RecordTypeRegistry.builtIn.keys, codesWithSpecs)
        RecordTypeRegistry.builtIn.values.forEach { definition ->
            assertEquals(
                definition.sensitive,
                definition.code in SensitiveTypes.HIGH_SENSITIVITY_TYPES
            )
            assertTrue(definition.label.isNotBlank())
        }
    }

    @Test
    fun unknownTypeUsesFallbackInsteadOfCrashing() {
        assertFalse(RecordTypeUiRegistry.has("future_type"))
        assertEquals(RecordTypeUiRegistry.fallback.icon, RecordTypeUiRegistry.iconOf("future_type"))
    }
}
