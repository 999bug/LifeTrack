package com.example.lifetrack.core.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 向 Gitee Releases 查询最新版本，作为国内网络下的主更新源。
 *
 * 公开仓库的 `releases/latest` 无需令牌；仅做 HTTPS GET，不携带任何用户数据、
 * 设备标识、Cookie 或遥测。失败、暂无 Release 或未附 APK 时由 [ReleaseFetcher] 回退 GitHub。
 */
object GiteeReleaseChecker : ReleaseSource {
    private const val ENDPOINT =
        "https://gitee.com/api/v5/repos/saul999/LifeTrack/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    override suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LifeTrack-Android")
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { ReleasePayloadParser.parse(it.readText()) }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IllegalStateException("Gitee API returned ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
