package com.example.lifetrack.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManifestReleaseCheckerTest {

    private val apkUrl =
        "https://gitee.com/saul999/LifeTrack/releases/download/v0.6.5/LifeTrack-0.6.5-release.apk"
    private val shaUrl =
        "https://gitee.com/saul999/LifeTrack/releases/download/v0.6.5/LifeTrack-0.6.5-release.apk.sha256"

    @Test
    fun parsesValidManifestIntoGiteeReleaseWithArtifact() {
        val json = """
        {
          "version": "0.6.5",
          "tag": "v0.6.5",
          "notes": "本次更新内容",
          "apkUrl": "$apkUrl",
          "sha256Url": "$shaUrl"
        }
        """.trimIndent()

        val info = ManifestReleaseChecker.parse(json)

        assertEquals("0.6.5", info?.version)
        assertEquals("v0.6.5", info?.tagName)
        assertEquals(ReleaseOrigin.GITEE, info?.origin)
        assertEquals(apkUrl, info?.artifact?.downloadUrl)
        assertEquals(shaUrl, info?.artifact?.checksumUrl)
    }

    @Test
    fun derivesTagFromVersionWhenTagAbsent() {
        val json = """{"version":"0.6.5","apkUrl":"$apkUrl","sha256Url":"$shaUrl"}"""
        assertEquals("v0.6.5", ManifestReleaseChecker.parse(json)?.tagName)
    }

    @Test
    fun returnsNullWhenApkUrlMissing() {
        val json = """{"version":"0.6.5","tag":"v0.6.5","sha256Url":"$shaUrl"}"""
        assertNull(ManifestReleaseChecker.parse(json))
    }

    @Test
    fun returnsNullWhenSha256UrlMissing() {
        val json = """{"version":"0.6.5","tag":"v0.6.5","apkUrl":"$apkUrl"}"""
        assertNull(ManifestReleaseChecker.parse(json))
    }

    @Test
    fun returnsNullOnMalformedJson() {
        assertNull(ManifestReleaseChecker.parse("not json{"))
    }

    @Test
    fun returnsNullWhenVersionIsBlank() {
        val json = """{"version":"  ","apkUrl":"$apkUrl","sha256Url":"$shaUrl"}"""
        assertNull(ManifestReleaseChecker.parse(json))
    }

    @Test
    fun returnsNullWhenTopLevelIsArray() {
        assertNull(ManifestReleaseChecker.parse("""[{"version":"0.6.5"}]"""))
    }

    @Test
    fun rejectsArtifactUrlOutsideGitee() {
        val evil = "https://evil.example.com/LifeTrack-0.6.5-release.apk"
        val json = """{"version":"0.6.5","apkUrl":"$evil","sha256Url":"$shaUrl"}"""
        assertNull(ManifestReleaseChecker.parse(json))
    }

    @Test
    fun rejectsPlainHttpArtifactUrl() {
        val plain = "http://gitee.com/saul999/LifeTrack/releases/download/v0.6.5/a.apk"
        val json = """{"version":"0.6.5","apkUrl":"$plain","sha256Url":"$shaUrl"}"""
        assertNull(ManifestReleaseChecker.parse(json))
    }

    @Test
    fun trimsWhitespaceAroundVersionAndUrls() {
        val json = """{"version":" 0.6.5 ","apkUrl":" $apkUrl ","sha256Url":" $shaUrl "}"""
        val info = ManifestReleaseChecker.parse(json)
        assertEquals("0.6.5", info?.version)
        assertEquals(apkUrl, info?.artifact?.downloadUrl)
        assertEquals(shaUrl, info?.artifact?.checksumUrl)
    }
}
