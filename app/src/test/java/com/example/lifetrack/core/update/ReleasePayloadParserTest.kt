package com.example.lifetrack.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasePayloadParserTest {

    @Test
    fun parsesGiteePayloadAndIgnoresSourceArchives() {
        val json = """
            {
              "id": 599072,
              "tag_name": "v0.4.0",
              "body": "修复若干问题",
              "assets": [
                {"name": "v0.4.0.zip", "browser_download_url": "https://gitee.com/saul999/LifeTrack/archive/refs/tags/v0.4.0.zip"},
                {"name": "LifeTrack-0.4.0-release.apk", "browser_download_url": "https://gitee.com/saul999/LifeTrack/releases/download/v0.4.0/LifeTrack-0.4.0-release.apk"},
                {"name": "LifeTrack-0.4.0-release.apk.sha256", "browser_download_url": "https://gitee.com/saul999/LifeTrack/releases/download/v0.4.0/LifeTrack-0.4.0-release.apk.sha256"}
              ]
            }
        """.trimIndent()

        val release = ReleasePayloadParser.parse(json)

        assertEquals("0.4.0", release?.version)
        assertEquals("v0.4.0", release?.tagName)
        assertEquals("修复若干问题", release?.notes)
        assertEquals(
            "https://gitee.com/saul999/LifeTrack/releases/download/v0.4.0/LifeTrack-0.4.0-release.apk",
            release?.downloadUrl
        )
    }

    @Test
    fun parsesGitHubPayload() {
        val json = """
            {
              "tag_name": "v0.5.0",
              "body": "English notes",
              "assets": [
                {"name": "LifeTrack-0.5.0-release.apk.sha256", "browser_download_url": "https://github.com/999bug/LifeTrack/releases/download/v0.5.0/checksum"},
                {"name": "LifeTrack-0.5.0-release.apk", "browser_download_url": "https://github.com/999bug/LifeTrack/releases/download/v0.5.0/LifeTrack-0.5.0-release.apk"}
              ]
            }
        """.trimIndent()

        val release = ReleasePayloadParser.parse(json)

        assertEquals("0.5.0", release?.version)
        assertEquals(
            "https://github.com/999bug/LifeTrack/releases/download/v0.5.0/LifeTrack-0.5.0-release.apk",
            release?.downloadUrl
        )
    }

    @Test
    fun returnsNullUrlWhenOnlySourceArchivesAreAttached() {
        val json = """
            {
              "tag_name": "v0.4.0",
              "assets": [
                {"name": "v0.4.0.zip", "browser_download_url": "https://gitee.com/example/zip"},
                {"name": "v0.4.0.tar.gz", "browser_download_url": "https://gitee.com/example/tar"}
              ]
            }
        """.trimIndent()

        assertNull(ReleasePayloadParser.parse(json)?.downloadUrl)
    }

    @Test
    fun returnsNullWhenTagNameMissing() {
        val json = """{"body": "no tag", "assets": []}"""

        assertNull(ReleasePayloadParser.parse(json))
    }

    @Test
    fun treatsJsonNullBodyAsNoNotes() {
        val json = """{"tag_name": "v0.4.0", "body": null, "assets": []}"""

        assertNull(ReleasePayloadParser.parse(json)?.notes)
    }

    @Test
    fun returnsNullForNonObjectPayload() {
        assertNull(ReleasePayloadParser.parse("[]"))
    }
}
