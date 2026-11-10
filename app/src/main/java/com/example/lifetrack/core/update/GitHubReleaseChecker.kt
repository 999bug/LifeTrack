package com.example.lifetrack.core.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 向 GitHub Releases 查询最新版本，作为 Gitee 不可用时的兜底源。
 *
 * 这是本项目仅有的两处主动网络请求之一，仅做 HTTPS GET，不携带任何用户数据、
 * 设备标识、Cookie 或遥测，满足 Local First 原则——用户数据始终只在本地。
 * [isNewer] 是与来源无关的版本比较工具，保留在此供主流程与既有测试调用。
 */
object GitHubReleaseChecker : ReleaseSource {
    private const val ENDPOINT = "https://api.github.com/repos/999bug/LifeTrack/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    override suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LifeTrack-Android")
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { ReleasePayloadParser.parse(it.readText()) }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IllegalStateException("GitHub API returned ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 比较 [latest] 是否比 [current] 更新。版本号按 `.` 分段逐个比较，前缀 `v` 会被忽略。
     * @return true 表示 [latest] 比 [current] 更新。
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val length = maxOf(a.size, b.size)
        for (i in 0 until length) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
