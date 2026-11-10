package com.example.lifetrack.core.update

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载本应用自己的更新包 APK 到应用的外部私有目录 `updates/`。
 * 不依赖 DownloadManager，便于实时回报进度；下载失败或内容校验不通过时删除半成品文件。
 *
 * 国内分发走 Gitee，附件地址可能被网关重定向到登录页并返回 HTML，
 * 因此写盘前检查响应状态与 Content-Type，写盘后由 [ApkFileValidator] 校验 ZIP 头与体积。
 */
object ApkDownloader {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 8192

    /**
     * @param fileName 保存的文件名，通常形如 `LifeTrack-<版本>.apk`
     * @param onProgress 回报已下载字节与总字节（总字节未知时为 0）
     * @return 下载完成的 File
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val output = File(dir, fileName)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "LifeTrack-Android")
            setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Download failed with HTTP ${connection.responseCode}")
            }
            val contentType = connection.contentType.orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw IllegalStateException("Download rejected: server returned HTML ($contentType)")
            }
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            connection.inputStream.use { input ->
                output.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            ApkFileValidator.validate(output)
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            connection.disconnect()
        }
        output
    }
}

/**
 * APK 内容的最小校验：ZIP 魔数 + 体积下限。
 * 只用于拦住「网关返回 HTML 或错误页却被当成 APK 交给安装器」，不替代系统对 APK 签名的校验。
 */
object ApkFileValidator {
    private const val MIN_APK_BYTES = 1_000_000L
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun validate(file: File) {
        val size = file.length()
        if (size < MIN_APK_BYTES) {
            throw IllegalStateException("Downloaded file is too small to be an APK: $size bytes")
        }
        val header = ByteArray(ZIP_MAGIC.size)
        val read = file.inputStream().use { it.read(header) }
        if (read != ZIP_MAGIC.size || !header.contentEquals(ZIP_MAGIC)) {
            throw IllegalStateException("Downloaded file is not an APK: missing ZIP magic")
        }
    }
}
