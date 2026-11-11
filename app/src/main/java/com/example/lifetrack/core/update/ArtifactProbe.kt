package com.example.lifetrack.core.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** APK 体积下限，预检与写盘校验共用同一条判据。 */
const val MIN_APK_BYTES = 1_000_000L

sealed interface ProbeResult {
    data class Ready(val sizeBytes: Long) : ProbeResult

    /** 附件暂时不可用（仍在同步、被重定向到登录页、源端 5xx），可退避重试。 */
    data class NotReady(val reason: String) : ProbeResult

    /** 附件存在但内容明显不合法，重试也不会变好，直接报错。 */
    data class Invalid(val reason: String) : ProbeResult
}

/**
 * 把 HTTP 响应归类成「可下载 / 未就绪 / 不合法」的纯函数。
 * 200 不等于成功：网关把登录页塞在 200 里、附件还没挂上时的 404、CDN 重定向
 * 都是同步窗口期的正常表现，必须和真正的损坏区分开。
 */
object ProbeClassifier {
    fun classify(
        responseCode: Int,
        contentType: String?,
        contentLength: Long,
        minBytes: Long = MIN_APK_BYTES
    ): ProbeResult = when {
        responseCode in 300..399 -> ProbeResult.NotReady("附件尚未就绪（HTTP $responseCode 重定向）")
        responseCode !in 200..299 -> ProbeResult.NotReady("附件暂时不可用（HTTP $responseCode）")
        contentType.orEmpty().contains("text/html", ignoreCase = true) ->
            ProbeResult.NotReady("附件返回了网页，可能仍在同步或需要登录")
        contentLength < minBytes -> ProbeResult.Invalid("附件大小异常：$contentLength 字节")
        else -> ProbeResult.Ready(contentLength)
    }
}

/** 制品探测与校验文件读取。抽成接口便于替换实现，也便于纯 JVM 测试覆盖判定逻辑。 */
interface ArtifactProbe {
    suspend fun probe(artifact: ApkArtifact): ProbeResult
    suspend fun fetchChecksum(url: String): String
}

/**
 * 用 HEAD 预检 APK 直链，不下载包体。
 * Gitee 附件会 302 到 `attach_files/.../download`、再 302 到带 token 的 CDN，
 * 最终响应应给出 200 + 明确的 Content-Length；中间态会被判为 NotReady。
 */
object HttpArtifactProbe : ArtifactProbe {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_CHECKSUM_CHARS = 512

    override suspend fun probe(artifact: ApkArtifact): ProbeResult = withContext(Dispatchers.IO) {
        val connection = (URL(artifact.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LifeTrack-Android")
            setRequestProperty(
                "Accept",
                "application/vnd.android.package-archive, application/zip, application/octet-stream, */*"
            )
        }
        try {
            ProbeClassifier.classify(
                responseCode = connection.responseCode,
                contentType = connection.contentType,
                contentLength = connection.contentLengthLong
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ProbeResult.NotReady(e.message ?: "附件预检失败")
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun fetchChecksum(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LifeTrack-Android")
            setRequestProperty("Accept", "text/plain, */*")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Checksum download failed with HTTP ${connection.responseCode}")
            }
            if (connection.contentType.orEmpty().contains("text/html", ignoreCase = true)) {
                throw IllegalStateException("Checksum download returned HTML")
            }
            val chars = CharArray(MAX_CHECKSUM_CHARS)
            val read = connection.inputStream.bufferedReader().use { it.read(chars) }
            if (read <= 0) throw IllegalStateException("Checksum file is empty")
            String(chars, 0, read)
        } finally {
            connection.disconnect()
        }
    }
}
