package com.example.lifetrack.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class RecordTypeRegistryTest {
    @Test
    fun everyDetailTypeHasDefinition() {
        assertTrue(RecordTypeRegistry.validate().isEmpty())
    }
}
