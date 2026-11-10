package com.example.lifetrack.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 解析 Release 清单。Gitee 与 GitHub 的 `releases/latest` 都返回
 * `tag_name` / `body` / `assets[].name` / `assets[].browser_download_url`，
 * 因此两端共用一套解析。只取 `.apk` 附件，跳过平台自动附带的 zip / tar.gz 源码包。
 */
object ReleasePayloadParser {
    fun parse(json: String): ReleaseInfo? {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val tagName = root.stringOrNull("tag_name") ?: return null
        val downloadUrl = (root["assets"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.stringOrNull("name")?.endsWith(".apk", ignoreCase = true) == true }
            ?.stringOrNull("browser_download_url")
        return ReleaseInfo(
            version = tagName.removePrefix("v"),
            tagName = tagName,
            downloadUrl = downloadUrl,
            notes = root.stringOrNull("body")
        )
    }

    /** JSON null 与字段缺失都按「没有值」处理，避免 JsonNull.content 抛异常。 */
    private fun JsonObject.stringOrNull(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}
