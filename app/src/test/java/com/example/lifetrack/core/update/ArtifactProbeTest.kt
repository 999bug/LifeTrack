package com.example.lifetrack.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactProbeTest {

    private val apkSize = 22_299_731L

    @Test
    fun acceptsRealGiteeCdnResponse() {
        val result = ProbeClassifier.classify(200, "application/zip", apkSize)

        assertEquals(ProbeResult.Ready(apkSize), result)
    }

    @Test
    fun treatsMissingAttachmentAsNotReady() {
        assertTrue(ProbeClassifier.classify(404, "text/html; charset=utf-8", 0) is ProbeResult.NotReady)
    }

    @Test
    fun treatsRedirectAsNotReady() {
        // Gitee 附件会先 302 到登录页或 CDN；跟随到中间态时不能当成可下载。
        assertTrue(ProbeClassifier.classify(302, "text/html", 0) is ProbeResult.NotReady)
    }

    @Test
    fun treatsServerErrorAsNotReady() {
        assertTrue(ProbeClassifier.classify(503, null, 0) is ProbeResult.NotReady)
    }

    @Test
    fun treatsHtmlBodyWithStatus200AsNotReady() {
        // HTTP 200 不代表成功：网关把登录页塞在 200 里是常见中间态。
        assertTrue(ProbeClassifier.classify(200, "text/html; charset=utf-8", apkSize) is ProbeResult.NotReady)
    }

    @Test
    fun rejectsImplausiblySmallAttachment() {
        assertTrue(ProbeClassifier.classify(200, "application/zip", 4096) is ProbeResult.Invalid)
    }

    @Test
    fun rejectsMissingContentLength() {
        assertTrue(ProbeClassifier.classify(200, "application/zip", -1) is ProbeResult.Invalid)
    }

    @Test
    fun acceptsMissingContentTypeWhenLengthIsValid() {
        assertEquals(
            ProbeResult.Ready(apkSize),
            ProbeClassifier.classify(200, null, apkSize)
        )
    }
}
