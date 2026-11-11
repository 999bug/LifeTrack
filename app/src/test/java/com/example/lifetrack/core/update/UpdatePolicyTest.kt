package com.example.lifetrack.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val artifact = ApkArtifact(
        downloadUrl = "https://gitee.com/example/app.apk",
        checksumUrl = "https://gitee.com/example/app.apk.sha256"
    )

    private fun release(
        version: String = "0.6.2",
        origin: ReleaseOrigin = ReleaseOrigin.GITEE,
        apk: ApkArtifact? = artifact
    ) = ReleaseInfo(
        version = version,
        tagName = "v$version",
        notes = null,
        origin = origin,
        artifact = apk
    )

    @Test
    fun returnsUpToDateWhenNoRelease() {
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(null, "0.6.1"))
    }

    @Test
    fun returnsUpToDateWhenVersionIsNotNewer() {
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(release("0.6.1"), "0.6.1"))
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.decide(release("0.5.9"), "0.6.1"))
    }

    @Test
    fun marksSyncWindowAsPendingEvenWhenVersionIsNewer() {
        // 回归核心：版本已发布但 sha256 还没同步过来，不能给下载按钮。
        val decision = UpdatePolicy.decide(release(apk = null), "0.6.1")

        assertTrue(decision is UpdateDecision.Pending)
        assertEquals(PendingReason.ARTIFACT_SYNCING, (decision as UpdateDecision.Pending).reason)
    }

    @Test
    fun marksFallbackOnlyAsPendingEvenWhenArtifactExists() {
        // GitHub 兜底只提供版本事实，国内版不跨境下载。
        val decision = UpdatePolicy.decide(
            release(origin = ReleaseOrigin.GITHUB, apk = artifact),
            "0.6.1"
        )

        assertTrue(decision is UpdateDecision.Pending)
        assertEquals(PendingReason.FALLBACK_ONLY, (decision as UpdateDecision.Pending).reason)
    }

    @Test
    fun allowsDownloadOnlyForGiteeWithCompleteArtifact() {
        val decision = UpdatePolicy.decide(release(), "0.6.1")

        assertTrue(decision is UpdateDecision.Available)
        assertEquals(artifact, (decision as UpdateDecision.Available).artifact)
    }

    @Test
    fun comparesVersionSegmentsNumerically() {
        assertEquals(
            UpdateDecision.UpToDate,
            UpdatePolicy.decide(release("0.9.0"), "0.10.0")
        )
        assertTrue(UpdatePolicy.decide(release("0.10.0"), "0.9.0") is UpdateDecision.Available)
    }
}
