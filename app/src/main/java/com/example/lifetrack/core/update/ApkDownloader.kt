package com.example.lifetrack.core.update

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载本应用自己的更新包 APK 到应用的外部私有目录 `updates/`。
 *
 * 关键约束：**所有前置校验都在 [onStart] 之前完成**，即进度条只会在确认源端
 * 真的有一个完整 APK 之后才出现。以前是打开 GET 就切到下载中，
 * 同步窗口期下到登录页 / 境外慢速包时，用户看到的就是「没包却缓慢走进度」。
 */
object ApkDownloader {
    private const val CONNECT_TIMEOUT_MS = 15_000

    /** socket 层无进展超时：单次 read 30 秒没有字节即失败。 */
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 8192

    /** 应用层无进展窗口：挡住「每 20 秒吐几个字节」的慢速吊命连接。 */
    private const val STALL_WINDOW_MS = 20_000L
    private const val MIN_WINDOW_BYTES = 32 * 1024L

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * @param expectedSize HEAD 预检拿到的字节数，作为进度的唯一分母
     * @param onStart 前置校验全部通过后调用，用于切换到「下载中」状态
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        expectedSize: Long,
        onStart: () -> Unit,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(expectedSize >= MIN_APK_BYTES) { "Invalid expected APK size: $expectedSize" }
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val output = File(dir, fileName)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LifeTrack-Android")
            setRequestProperty(
                "Accept",
                "application/vnd.android.package-archive, application/zip, application/octet-stream, */*"
            )
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Download failed with HTTP $responseCode")
            }
            val contentType = connection.contentType.orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw IllegalStateException("Download rejected: server returned HTML ($contentType)")
            }
            val declared = connection.contentLengthLong
            if (declared > 0 && declared != expectedSize) {
                throw IllegalStateException("Download size mismatch: expected $expectedSize, got $declared")
            }

            connection.inputStream.use { input ->
                val header = ByteArray(ZIP_MAGIC.size)
                var headerRead = 0
                while (headerRead < header.size) {
                    val read = input.read(header, headerRead, header.size - headerRead)
                    if (read == -1) break
                    headerRead += read
                }
                if (headerRead != header.size || !header.contentEquals(ZIP_MAGIC)) {
                    throw IllegalStateException("Download rejected: missing ZIP magic")
                }

                // 状态、类型、长度、ZIP 头全部通过，才允许 UI 显示进度条。
                onStart()

                output.outputStream().use { out ->
                    out.write(header)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = headerRead.toLong()
                    var windowStartedAt = System.currentTimeMillis()
                    var windowBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded > expectedSize) {
                            throw IllegalStateException("Download exceeded expected size: $downloaded > $expectedSize")
                        }
                        onProgress(downloaded, expectedSize)
                        windowBytes += read
                        val now = System.currentTimeMillis()
                        if (now - windowStartedAt >= STALL_WINDOW_MS) {
                            if (windowBytes < MIN_WINDOW_BYTES) {
                                throw IllegalStateException(
                                    "Download stalled: only $windowBytes bytes in ${now - windowStartedAt}ms"
                                )
                            }
                            windowStartedAt = now
                            windowBytes = 0
                        }
                    }
                }
            }
            val actual = output.length()
            if (actual != expectedSize) {
                throw IllegalStateException("Incomplete download: $actual / $expectedSize bytes")
            }
            output
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * 写盘后的完整校验：ZIP 魔数 + 体积下限 + sha256。
 * sha256 只证明字节完整，身份边界仍是 APK 签名，由系统在安装时校验。
 */
object ApkFileValidator {
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

    /** 解析 `sha256sum` 输出（`<64 位 hex>  文件名`），拒绝 HTML / 空内容 / 截断内容。 */
    fun parseSha256(text: String): String {
        val token = text.trim().lineSequence().firstOrNull()?.trim()
            ?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        if (!SHA256_HEX.matches(token)) {
            throw IllegalStateException("Invalid sha256 checksum content")
        }
        return token.lowercase()
    }

    fun validate(file: File, expectedSha256: String) {
        val size = file.length()
        if (size < MIN_APK_BYTES) {
            throw IllegalStateException("Downloaded file is too small to be an APK: $size bytes")
        }
        val header = ByteArray(ZIP_MAGIC.size)
        val read = file.inputStream().use { it.read(header) }
        if (read != ZIP_MAGIC.size || !header.contentEquals(ZIP_MAGIC)) {
            throw IllegalStateException("Downloaded file is not an APK: missing ZIP magic")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw IllegalStateException("APK checksum mismatch: expected $expectedSha256, got $actual")
        }
    }
}
