package com.example.lifetrack.core.update

/**
 * 更新来源。国内分发只认 Gitee 的制品；GitHub 仅在主源故障时提供版本信息，不提供下载地址。
 */
enum class ReleaseOrigin { GITEE, GITHUB }

/**
 * 一个「已就绪」的安装包：APK 直链 + 同名 sha256 校验文件。
 *
 * `mirror-gitee` 在同一个 shell 步骤里先创建 Release、再逐个上传附件，
 * sha256 是最后上传的一份。它出现在 `assets` 里等价于「本次同步已完成」，
 * 因此解析层只在两份附件齐备时才构造本对象，避免把同步中间态当成可下载状态。
 */
data class ApkArtifact(
    val downloadUrl: String,
    val checksumUrl: String,
    val sizeBytes: Long? = null
)

/**
 * Release 清单解析结果。
 *
 * @param artifact 两份附件齐备时才非 null；为 null 表示「版本已发布，安装包尚未就绪」。
 */
data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val notes: String?,
    val origin: ReleaseOrigin,
    val artifact: ApkArtifact?
)
