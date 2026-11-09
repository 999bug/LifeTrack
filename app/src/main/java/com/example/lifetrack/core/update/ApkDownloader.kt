package com.example.lifetrack.core.update

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载本应用自己的更新包 APK 到应用的外部私有目录 `updates/`。
 * 不依赖 DownloadManager，便于实时回报进度；下载失败时删除半成品文件。
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
        }
        val total = connection.contentLengthLong.coerceAtLeast(0L)
        try {
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
        } catch (e: Exception) {
            output.delete()
            throw e
        }
        output
    }
}
