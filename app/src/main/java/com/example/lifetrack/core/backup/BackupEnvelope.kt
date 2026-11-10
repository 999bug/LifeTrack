package com.example.lifetrack.core.backup

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets

/**
 * v3 加密备份的外层容器：头部参数明文可见，正文是 AES-GCM 密文。
 *
 * 头部字段全部参与 AAD，因此改迭代次数、盐、IV 或导出时间都会让 GCM 标签校验失败，
 * 不会出现「改了头部还能解出另一份数据」的情况。
 *
 * 为什么正文是 Base64 字符串而不是二进制：备份走 SAF 导出一个 JSON 文件，
 * 保持纯文本能继续用 `CreateDocument("application/json")`，也便于用户自己确认文件没被截断。
 */
@Serializable
internal data class EncryptedBackupFileDto(
    val app: String = "",
    val schemaVersion: Int = 0,
    val exportedAt: String = "",
    val kdf: String = "",
    val iterations: Int = 0,
    val salt: String = "",
    val iv: String = "",
    val cipher: String = "",
    /** Base64(密文 + GCM 标签)。 */
    val payload: String = ""
)

internal object BackupEnvelope {

    const val SCHEMA_VERSION = 3

    /**
     * AAD 的规范形式。字段用 `|` 连接：各字段本身不含 `|`
     * （Base64 字母表、ISO-8601 时间、固定算法名都不会出现），不存在拼接歧义。
     */
    fun aad(
        app: String,
        schemaVersion: Int,
        exportedAt: String,
        kdf: String,
        iterations: Int,
        salt: String,
        iv: String,
        cipher: String
    ): ByteArray = buildString {
        append(app)
        append('|').append(schemaVersion)
        append('|').append(exportedAt)
        append('|').append(kdf)
        append('|').append(iterations)
        append('|').append(salt)
        append('|').append(iv)
        append('|').append(cipher)
    }.toByteArray(StandardCharsets.UTF_8)
}
