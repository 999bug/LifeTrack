package com.example.lifetrack.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingBackoffTest {

    @Test
    fun increasesWaitBetweenRetries() {
        assertEquals(30_000L, PendingBackoff.delayMillis(0))
        assertEquals(60_000L, PendingBackoff.delayMillis(1))
        assertEquals(120_000L, PendingBackoff.delayMillis(2))
        assertEquals(300_000L, PendingBackoff.delayMillis(3))
    }

    @Test
    fun capsWaitAfterLastStep() {
        assertEquals(300_000L, PendingBackoff.delayMillis(50))
    }

    @Test
    fun clampsNegativeAttempt() {
        assertEquals(30_000L, PendingBackoff.delayMillis(-1))
    }
}
