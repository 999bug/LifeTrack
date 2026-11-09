package com.example.lifetrack.core.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 向 GitHub Releases 查询最新版本。
 *
 * 这是本项目唯一主动发起的网络请求，仅做 HTTPS GET，不携带任何用户数据、
 * 设备标识、Cookie 或遥测，满足 Local First 原则——用户数据始终只在本地。
 */
object GitHubReleaseChecker {
    private const val ENDPOINT = "https://api.github.com/repos/999bug/LifeTrack/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * 查询最新 Release。
     * @return 解析出的 [ReleaseInfo]；仓库尚无任何 Release 时返回 null（API 返回 404）；
     *          其它网络或解析异常向上抛出，由调用方决定如何提示。
     */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LifeTrack-Android")
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parse(body)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IllegalStateException("GitHub API 返回 ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: String): ReleaseInfo? {
        val root = Json.parseToJsonElement(json)
        if (root !is JsonObject) return null
        val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return null
        val version = tagName.removePrefix("v")
        val notes = root["body"]?.jsonPrimitive?.content
        val downloadUrl = (root["assets"] as? JsonArray)
            ?.firstOrNull { asset ->
                val name = (asset as? JsonObject)?.get("name")?.jsonPrimitive?.content ?: ""
                name.endsWith(".apk", ignoreCase = true)
            }
            ?.let { (it as JsonObject).get("browser_download_url")?.jsonPrimitive?.content }
        return ReleaseInfo(version = version, tagName = tagName, downloadUrl = downloadUrl, notes = notes)
    }

    /**
     * 比较 [latest] 是否比 [current] 更新。版本号按 `.` 分段逐个比较，前缀 `v` 会被忽略。
     * @return true 表示 [latest] 比 [current] 更新。
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        val length = maxOf(a.size, b.size)
        for (i in 0 until length) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
