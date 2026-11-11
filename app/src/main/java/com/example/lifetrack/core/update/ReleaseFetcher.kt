package com.example.lifetrack.core.update

/**
 * 更新来源。Gitee 为国内主源，GitHub 为版本信息兜底源。
 */
interface ReleaseSource {
    suspend fun fetchLatest(): ReleaseInfo?
}

/**
 * 国内分发的版本查询：Gitee 为主源，GitHub 仅在主源不可用时提供版本信息。
 *
 * 主源「请求失败 / 暂无 Release」与「返回了 Release 但附件还没同步过来」是两件事：
 * 前者才回退 GitHub，后者直接返回主源结果，由 [UpdatePolicy] 判为 Pending。
 * 绝不拿 GitHub 的 APK 地址顶上——那会让国内用户去下境外 22MB 的包，
 * 表现为「源端没包却提示可更新，点下去进度条缓慢地走」。
 *
 * 回退结果一律剥掉制品，国内版不跨境下载；用户可从设置页手动前往发布页。
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
        // 主源只要给出了版本事实就以其为准，缺附件是同步中间态，不是故障。
        if (primaryRelease != null) return primaryRelease

        return try {
            fallback.fetchLatest()?.copy(origin = ReleaseOrigin.GITHUB, artifact = null)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            primaryFailure?.let { e.addSuppressed(it) }
            throw e
        }
    }
}
