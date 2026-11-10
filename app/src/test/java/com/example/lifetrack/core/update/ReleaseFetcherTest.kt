package com.example.lifetrack.core.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeReleaseSource(
    private val release: ReleaseInfo? = null,
    private val failure: Throwable? = null
) : ReleaseSource {
    var calls = 0
        private set

    override suspend fun fetchLatest(): ReleaseInfo? {
        calls++
        failure?.let { throw it }
        return release
    }
}

class ReleaseFetcherTest {

    private fun apkRelease(version: String) = ReleaseInfo(
        version = version,
        tagName = "v$version",
        downloadUrl = "https://gitee.com/example/LifeTrack-$version.apk",
        notes = null
    )

    @Test
    fun usesPrimaryWithoutRequestingFallback() = runBlocking {
        val primary = FakeReleaseSource(release = apkRelease("0.5.0"))
        val fallback = FakeReleaseSource(release = apkRelease("0.4.0"))

        val result = ReleaseFetcher.fetchLatest(primary, fallback)

        assertEquals(apkRelease("0.5.0"), result)
        assertEquals(1, primary.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun fallsBackWhenPrimaryHasNoRelease() = runBlocking {
        val fallbackRelease = apkRelease("0.4.0")
        val primary = FakeReleaseSource(release = null)
        val fallback = FakeReleaseSource(release = fallbackRelease)

        assertEquals(fallbackRelease, ReleaseFetcher.fetchLatest(primary, fallback))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun fallsBackWhenPrimaryReleaseHasNoApk() = runBlocking {
        val fallbackRelease = apkRelease("0.4.0")
        val primary = FakeReleaseSource(
            release = ReleaseInfo(version = "0.4.0", tagName = "v0.4.0", downloadUrl = null, notes = null)
        )
        val fallback = FakeReleaseSource(release = fallbackRelease)

        assertEquals(fallbackRelease, ReleaseFetcher.fetchLatest(primary, fallback))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun fallsBackWhenPrimaryRequestFails() = runBlocking {
        val fallbackRelease = apkRelease("0.4.0")
        val primary = FakeReleaseSource(failure = IllegalStateException("Gitee unavailable"))
        val fallback = FakeReleaseSource(release = fallbackRelease)

        assertEquals(fallbackRelease, ReleaseFetcher.fetchLatest(primary, fallback))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun reportsBothFailuresWhenBothSourcesFail() {
        val primaryFailure = IllegalStateException("Gitee unavailable")
        val fallbackFailure = IllegalStateException("GitHub unavailable")
        val primary = FakeReleaseSource(failure = primaryFailure)
        val fallback = FakeReleaseSource(failure = fallbackFailure)

        val thrown = runCatching {
            runBlocking { ReleaseFetcher.fetchLatest(primary, fallback) }
        }.exceptionOrNull()

        assertTrue(thrown === fallbackFailure)
        assertTrue(fallbackFailure.suppressed.contains(primaryFailure))
    }

    @Test
    fun returnsNullWhenBothSourcesHaveNoRelease() = runBlocking {
        val primary = FakeReleaseSource(release = null)
        val fallback = FakeReleaseSource(release = null)

        assertNull(ReleaseFetcher.fetchLatest(primary, fallback))
    }
}
