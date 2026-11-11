package com.example.lifetrack.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePayloadParserTest {

    /** 2026-11-11 线上实测：v0.6.2 的 Gitee Release 已可见，但只有源码包。 */
    private val syncWindowPayload = """
        {
          "tag_name": "v0.6.2",
          "body": "国内分发镜像，权威发布见 https://github.com/999bug/LifeTrack/releases/tag/v0.6.2",
          "assets": [
            {"browser_download_url": "https://gitee.com/saul999/LifeTrack/archive/refs/tags/v0.6.2.zip", "name": "v0.6.2.zip"},
            {"browser_download_url": "https://gitee.com/saul999/LifeTrack/archive/refs/tags/v0.6.2.tar.gz", "name": "v0.6.2.tar.gz"}
          ]
        }
    """.trimIndent()

    private val completeGiteePayload = """
        {
          "tag_name": "v0.6.1",
          "body": "修复若干问题",
          "assets": [
            {"name": "v0.6.1.zip", "browser_download_url": "https://gitee.com/example/v0.6.1.zip"},
            {"name": "LifeTrack-0.6.1-release.apk", "browser_download_url": "https://gitee.com/example/LifeTrack-0.6.1-release.apk"},
            {"name": "LifeTrack-0.6.1-release.apk.sha256", "browser_download_url": "https://gitee.com/example/LifeTrack-0.6.1-release.apk.sha256"}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesCompleteGiteePayloadAndIgnoresSourceArchives() {
        val release = ReleasePayloadParser.parse(completeGiteePayload, ReleaseOrigin.GITEE)

        assertEquals("0.6.1", release?.version)
        assertEquals("v0.6.1", release?.tagName)
        assertEquals("修复若干问题", release?.notes)
        assertEquals(ReleaseOrigin.GITEE, release?.origin)
        assertEquals("https://gitee.com/example/LifeTrack-0.6.1-release.apk", release?.artifact?.downloadUrl)
        assertEquals(
            "https://gitee.com/example/LifeTrack-0.6.1-release.apk.sha256",
            release?.artifact?.checksumUrl
        )
        assertNull(release?.artifact?.sizeBytes)
    }

    @Test
    fun parsesGitHubPayloadWithAssetSize() {
        val json = """
            {
              "tag_name": "v0.6.2",
              "assets": [
                {"name": "LifeTrack-0.6.2-release.apk.sha256", "browser_download_url": "https://github.com/example/checksum"},
                {"name": "LifeTrack-0.6.2-release.apk", "browser_download_url": "https://github.com/example/app.apk", "size": 22332499}
              ]
            }
        """.trimIndent()

        val release = ReleasePayloadParser.parse(json, ReleaseOrigin.GITHUB)

        assertEquals(ReleaseOrigin.GITHUB, release?.origin)
        assertEquals("https://github.com/example/app.apk", release?.artifact?.downloadUrl)
        assertEquals(22_332_499L, release?.artifact?.sizeBytes)
    }

    @Test
    fun treatsRealSyncWindowAsArtifactNotReady() {
        val release = ReleasePayloadParser.parse(syncWindowPayload, ReleaseOrigin.GITEE)

        assertEquals("0.6.2", release?.version)
        assertNull(release?.artifact)
    }

    @Test
    fun treatsApkWithoutChecksumAsArtifactNotReady() {
        // sha256 是最后上传的附件，它没出现就说明同步还没完成。
        val json = """
            {"tag_name": "v0.6.2", "assets": [
              {"name": "LifeTrack-0.6.2-release.apk", "browser_download_url": "https://gitee.com/example/app.apk"}
            ]}
        """.trimIndent()

        assertNull(ReleasePayloadParser.parse(json, ReleaseOrigin.GITEE)?.artifact)
    }

    @Test
    fun returnsNullWhenTagNameMissing() {
        assertNull(ReleasePayloadParser.parse("""{"body": "no tag", "assets": []}""", ReleaseOrigin.GITEE))
    }

    @Test
    fun treatsJsonNullBodyAsNoNotes() {
        val json = """{"tag_name": "v0.4.0", "body": null, "assets": []}"""

        assertNull(ReleasePayloadParser.parse(json, ReleaseOrigin.GITEE)?.notes)
    }

    @Test
    fun returnsNullForNonObjectPayload() {
        assertNull(ReleasePayloadParser.parse("[]", ReleaseOrigin.GITEE))
    }

    @Test
    fun keepsVersionFactEvenWhenArtifactMissing() {
        val release = ReleasePayloadParser.parse(syncWindowPayload, ReleaseOrigin.GITEE)

        assertTrue(release != null)
        assertEquals("v0.6.2", release?.tagName)
    }
}
