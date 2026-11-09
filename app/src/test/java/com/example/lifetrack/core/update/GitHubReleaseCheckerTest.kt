package com.example.lifetrack.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {

    @Test
    fun newer_whenHigherPatch() {
        assertTrue(GitHubReleaseChecker.isNewer("0.1.1", "0.1.0"))
    }

    @Test
    fun newer_whenHigherMinor() {
        assertTrue(GitHubReleaseChecker.isNewer("0.2.0", "0.1.0"))
    }

    @Test
    fun newer_whenHigherMajor() {
        assertTrue(GitHubReleaseChecker.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun newer_ignoresVersionPrefix() {
        assertTrue(GitHubReleaseChecker.isNewer("v0.2.0", "0.1.0"))
    }

    @Test
    fun notNewer_whenEqual() {
        assertFalse(GitHubReleaseChecker.isNewer("0.1.0", "0.1.0"))
    }

    @Test
    fun notNewer_whenOlder() {
        assertFalse(GitHubReleaseChecker.isNewer("0.1.0", "0.2.0"))
    }

    @Test
    fun notNewer_whenShorterTailEqual() {
        assertFalse(GitHubReleaseChecker.isNewer("0.1", "0.1.0"))
    }
}
