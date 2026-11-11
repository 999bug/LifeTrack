package com.example.lifetrack.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 解析 Release 清单。Gitee 与 GitHub 的 `releases/latest` 都返回
 * `tag_name` / `body` / `assets[].name` / `assets[].browser_download_url`，共用一套解析。
 *
 * 只认 `.apk` 与 `.apk.sha256` 两份附件；源码 zip / tar.gz 是平台自动附加的，
 * 不作为制品就绪的判据。
 */
object ReleasePayloadParser {
    fun parse(json: String, origin: ReleaseOrigin): ReleaseInfo? {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val tagName = root.stringOrNull("tag_name") ?: return null
        val assets = (root["assets"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

        fun assetEndingWith(suffix: String): JsonObject? =
            assets.firstOrNull { it.stringOrNull("name")?.endsWith(suffix, ignoreCase = true) == true }

        val apk = assetEndingWith(".apk")
        val checksum = assetEndingWith(".apk.sha256")
        val apkUrl = apk?.stringOrNull("browser_download_url")?.takeIf { it.isNotBlank() }
        val checksumUrl = checksum?.stringOrNull("browser_download_url")?.takeIf { it.isNotBlank() }

        // sha256 是同步完成标记：缺任一份附件都视为「版本已发布、制品未就绪」。
        val artifact = if (apkUrl != null && checksumUrl != null) {
            ApkArtifact(
                downloadUrl = apkUrl,
                checksumUrl = checksumUrl,
                sizeBytes = apk.longOrNull("size")
            )
        } else {
            null
        }

        return ReleaseInfo(
            version = tagName.removePrefix("v"),
            tagName = tagName,
            notes = root.stringOrNull("body"),
            origin = origin,
            artifact = artifact
        )
    }

    private fun JsonObject.longOrNull(key: String): Long? =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toLongOrNull()

    /** JSON null 与字段缺失都按「没有值」处理，避免 JsonNull.content 抛异常。 */
    private fun JsonObject.stringOrNull(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}
