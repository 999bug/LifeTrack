package com.example.lifetrack.core.update

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 国内分发的主更新源：只读一个放在 Gitee 文件 CDN 上的轻量 `latest.json`，
 * 不再调用 Gitee 的 `/api/v5/.../releases/latest` 接口——后者对匿名请求限流极严
 * （实测返回 403 Rate Limit Exceeded），会让客户端把「包明明在 Gitee 上」误判成
 * 「国内分发源不可用」。raw 文件走 CDN，与限流的 REST API 不是同一套入口，不受其影响。
 *
 * 清单只含版本事实与 Gitee 的 APK / sha256 直链；真正的「包可不可下」仍由
 * [HttpArtifactProbe] 对直链做 HEAD 预检决定。所以即便清单声明有包、CDN 还没就绪，
 * 也只会进入 Pending，而不会误报可下载。
 *
 * 清单抓取失败时返回 null，由 [ReleaseFetcher] 回退到 GitHub 取版本信息（仅版本事实，不下载）。
 */
object ManifestReleaseChecker : ReleaseSource {
    private const val MANIFEST_URL =
        "https://gitee.com/saul999/LifeTrack/raw/main/latest.json"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    /**
     * 只接受 Gitee 自己的下载域名。
     *
     * 清单是仓库里的一个可写文件，客户端会照着里面的 `apkUrl` 下载并调起安装；
     * 而 `sha256Url` 与它同源，校验挡不住「整份清单被写歪」。所以这里先做一道
     * host 白名单，至少不允许把用户导去任意第三方地址。
     */
    private val ALLOWED_HOSTS = listOf("gitee.com", "giteeusercontent.com")

    override suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        // 带时间戳绕开 CDN 缓存：刚发完版 raw 文件可能还是旧的，客户端不能靠它判断有没有新版。
        // 请求量很小（一天个位数），回源的代价远低于「发版后长时间看不到更新」。
        val connection = (URL("$MANIFEST_URL?t=${System.currentTimeMillis()}")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("User-Agent", "LifeTrack-Android")
            // 上面已用时间戳做 cache-buster，这里再显式禁止 HTTP 层缓存。
            useCaches = false
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { parse(it.readText()) }
                else -> null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 纯解析：坏清单（缺字段 / 非法 JSON / 域名不对）一律返回 null，交给 GitHub 兜底。
     * 不抛异常，避免一次脏数据把整个主源判死。
     */
    internal fun parse(json: String): ReleaseInfo? {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            return null
        } as? JsonObject ?: return null
        val version = root.stringOrNull("version")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val apkUrl = giteeUrl(root.stringOrNull("apkUrl")) ?: return null
        val shaUrl = giteeUrl(root.stringOrNull("sha256Url")) ?: return null
        return ReleaseInfo(
            version = version,
            tagName = root.stringOrNull("tag")?.trim()?.takeIf { it.isNotBlank() } ?: "v$version",
            notes = root.stringOrNull("notes"),
            origin = ReleaseOrigin.GITEE,
            artifact = ApkArtifact(downloadUrl = apkUrl, checksumUrl = shaUrl)
        )
    }

    /**
     * 只放行 Gitee 域名下的 https 绝对地址；其余（http、相对路径、别家域名）当脏数据。
     *
     * 白名单写死在客户端，日后 Gitee 换下载域名时需要同步改这里——宁可先挡掉不合规的
     * 清单让用户走 GitHub 兜底，也不要照着来历不明的地址装包。
     */
    private fun giteeUrl(raw: String?): String? {
        val candidate = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val allowed = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
        return candidate.takeIf { allowed }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}
