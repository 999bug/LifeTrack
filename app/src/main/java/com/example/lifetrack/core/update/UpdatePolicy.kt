package com.example.lifetrack.core.update

/** 判定结果：只有 [Available] 允许出现下载按钮。 */
sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    data class Pending(val release: ReleaseInfo, val reason: PendingReason) : UpdateDecision
    data class Available(val release: ReleaseInfo, val artifact: ApkArtifact) : UpdateDecision
}

enum class PendingReason {
    /** 主源已发布新版本，APK / sha256 附件还没同步过来。 */
    ARTIFACT_SYNCING,

    /** 制品预检未通过，附件暂时不可下载（404 / 登录页 / 重定向等）。 */
    PROBE_NOT_READY,

    /** 主源不可用，只剩 GitHub 的版本信息；国内版不跨境下载。 */
    FALLBACK_ONLY
}

/**
 * 纯判定层：把「版本新旧」「制品是否就绪」「来源能不能下载」收在一个函数里。
 * 不依赖 Android 框架，便于纯 JVM 单测覆盖 Gitee 同步窗口期。
 */
object UpdatePolicy {
    fun decide(release: ReleaseInfo?, currentVersion: String): UpdateDecision {
        if (release == null) return UpdateDecision.UpToDate
        if (!GitHubReleaseChecker.isNewer(release.version, currentVersion)) {
            return UpdateDecision.UpToDate
        }
        if (release.origin != ReleaseOrigin.GITEE) {
            return UpdateDecision.Pending(release, PendingReason.FALLBACK_ONLY)
        }
        val artifact = release.artifact
            ?: return UpdateDecision.Pending(release, PendingReason.ARTIFACT_SYNCING)
        return UpdateDecision.Available(release, artifact)
    }
}
