package com.example.lifetrack.core.update

/**
 * 更新来源。Gitee 为国内主源，GitHub 为兜底源，两者返回同一份 [ReleaseInfo]。
 */
interface ReleaseSource {
    suspend fun fetchLatest(): ReleaseInfo?
}

/**
 * 按「Gitee 优先、GitHub 兜底」取最新版本。
 *
 * 主源出现以下任一情况都回退：请求异常、仓库暂无 Release、Release 未附 APK。
 * 两个来源都失败时抛出兜底源的异常，并把主源失败挂为 suppressed 便于排查。
 * 主源有可用 APK 时不会请求兜底源，避免国内网络下多等一次 GitHub 超时。
 */
object ReleaseFetcher {
    suspend fun fetchLatest(
        primary: ReleaseSource = GiteeReleaseChecker,
        fallback: ReleaseSource = GitHubReleaseChecker
    ): ReleaseInfo? {
        var primaryFailure: Throwable? = null
        val primaryRelease = try {
            primary.fetchLatest()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            primaryFailure = e
            null
        }
        if (primaryRelease?.downloadUrl != null) return primaryRelease

        return try {
            fallback.fetchLatest()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            primaryFailure?.let { e.addSuppressed(it) }
            throw e
        }
    }
}
