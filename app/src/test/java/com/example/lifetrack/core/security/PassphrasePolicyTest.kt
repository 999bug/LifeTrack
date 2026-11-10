package com.example.lifetrack.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PassphrasePolicyTest {

    @Test
    fun quality_flagsTooShortBeforeScoring() {
        assertEquals(
            PassphraseQuality.Level.TOO_SHORT,
            PassphraseQuality.evaluate("abc123".toCharArray()).level
        )
    }

    @Test
    fun quality_weakWhenShortAndSingleClass() {
        assertEquals(
            PassphraseQuality.Level.WEAK,
            PassphraseQuality.evaluate("abcdefgh".toCharArray()).level
        )
    }

    @Test
    fun quality_fairForMixedEightChars() {
        assertEquals(
            PassphraseQuality.Level.FAIR,
            PassphraseQuality.evaluate("Abcd1234".toCharArray()).level
        )
    }

    @Test
    fun quality_strongForLongMixedPassphrase() {
        assertEquals(
            PassphraseQuality.Level.STRONG,
            PassphraseQuality.evaluate("Abcd1234!xyz".toCharArray()).level
        )
    }

    /** 中文按「非 ASCII」算一类，12 字应落在可用，而不是被当成弱口令。 */
    @Test
    fun quality_countsChineseSentenceAsFair() {
        assertEquals(
            PassphraseQuality.Level.FAIR,
            PassphraseQuality.evaluate("生活记录备份口令要长一点".toCharArray()).level
        )
    }

    @Test
    fun quality_longChineseSentenceIsStrong() {
        assertEquals(
            PassphraseQuality.Level.STRONG,
            PassphraseQuality.evaluate("今天天气不错我想备份一下生活记录".toCharArray()).level
        )
    }

    @Test
    fun retry_isFreeForFirstTwoFailures() {
        assertEquals(0L, RetryBackoff.delayMillisAfter(0))
        assertEquals(0L, RetryBackoff.delayMillisAfter(1))
        assertEquals(0L, RetryBackoff.delayMillisAfter(2))
    }

    @Test
    fun retry_growsExponentiallyAndCapsAtThirtySeconds() {
        assertEquals(2_000L, RetryBackoff.delayMillisAfter(3))
        assertEquals(4_000L, RetryBackoff.delayMillisAfter(4))
        assertEquals(8_000L, RetryBackoff.delayMillisAfter(5))
        assertEquals(16_000L, RetryBackoff.delayMillisAfter(6))
        assertEquals(30_000L, RetryBackoff.delayMillisAfter(7))
        assertEquals(30_000L, RetryBackoff.delayMillisAfter(100))
    }

    @Test
    fun retry_secondsAreRoundedUpForDisplay() {
        assertEquals(0, RetryBackoff.secondsAfter(2))
        assertEquals(2, RetryBackoff.secondsAfter(3))
        assertEquals(30, RetryBackoff.secondsAfter(9))
    }
}
