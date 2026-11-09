package com.example.lifetrack.core.update

/**
 * GitHub Releases 解析出的版本信息。
 *
 * @param version 去掉前导 `v` 后的纯版本号，如 `0.2.0`
 * @param tagName 原始 tag，如 `v0.2.0`
 * @param downloadUrl apk 资源的可下载地址；仓库未附 apk 时为 null
 * @param notes Release 的正文（发布说明），可能较长或为空
 */
data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val downloadUrl: String?,
    val notes: String?
)
