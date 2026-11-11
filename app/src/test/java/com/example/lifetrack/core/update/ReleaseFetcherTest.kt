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

    private fun artifact() = ApkArtifact(
        downloadUrl = "https://gitee.com/example/LifeTrack-0.6.2-release.apk",
        checksumUrl = "https://gitee.com/example/LifeTrack-0.6.2-release.apk.sha256"
    )

    private fun apkRelease(version: String, origin: ReleaseOrigin = ReleaseOrigin.GITEE) = ReleaseInfo(
        version = version,
        tagName = "v$version",
        notes = null,
        origin = origin,
        artifact = artifact()
    )

    /** 版本已发布、APK 附件还没同步过来的中间态。 */
    private fun pendingRelease(version: String) = ReleaseInfo(
        version = version,
        tagName = "v$version",
        notes = null,
        origin = ReleaseOrigin.GITEE,
        artifact = null
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
    fun doesNotFallBackWhenPrimaryArtifactIsStillSyncing() = runBlocking {
        // 回归：Gitee 已发布新版本但还没上传 APK 时，绝不能拿 GitHub 的包顶上。
        val primary = FakeReleaseSource(release = pendingRelease("0.6.2"))
        val fallback = FakeReleaseSource(release = apkRelease("0.6.2", ReleaseOrigin.GITHUB))

        val result = ReleaseFetcher.fetchLatest(primary, fallback)

        assertEquals(pendingRelease("0.6.2"), result)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun fallsBackWhenPrimaryHasNoRelease() = runBlocking {
        val primary = FakeReleaseSource(release = null)
        val fallback = FakeReleaseSource(release = apkRelease("0.4.0", ReleaseOrigin.GITHUB))

        val result = ReleaseFetcher.fetchLatest(primary, fallback)

        assertEquals("0.4.0", result?.version)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun fallsBackWhenPrimaryRequestFails() = runBlocking {
        val primary = FakeReleaseSource(failure = IllegalStateException("Gitee unavailable"))
        val fallback = FakeReleaseSource(release = apkRelease("0.4.0", ReleaseOrigin.GITHUB))

        val result = ReleaseFetcher.fetchLatest(primary, fallback)

        assertEquals("0.4.0", result?.version)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun fallbackNeverCarriesADownloadableArtifact() = runBlocking {
        // 国内版不跨境下载：兜底结果只保留版本事实，制品一律剥掉。
        val primary = FakeReleaseSource(release = null)
        val fallback = FakeReleaseSource(release = apkRelease("0.6.2", ReleaseOrigin.GITHUB))

        val result = ReleaseFetcher.fetchLatest(primary, fallback)

        assertNull(result?.artifact)
        assertEquals(ReleaseOrigin.GITHUB, result?.origin)
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
