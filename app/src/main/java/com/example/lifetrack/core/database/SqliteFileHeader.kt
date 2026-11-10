package com.example.lifetrack.core.database

import java.io.File

/**
 * 判断数据库文件是不是明文 SQLite：文件头固定为 `SQLite format 3\0`。
 *
 * 单独放在不依赖 Android 的纯 JVM 对象里，方便本地单测覆盖；加密后的 SQLCipher 文件头是随机密文，不会匹配。
 * 注意：文件不存在、被截断或全空时一律返回 false（视为非明文），由调用方决定如何处置。
 */
object SqliteFileHeader {

    const val HEADER_SIZE = 16

    private val PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun isPlaintext(header: ByteArray): Boolean =
        header.size >= HEADER_SIZE && PLAINTEXT_HEADER.indices.all { header[it] == PLAINTEXT_HEADER[it] }

    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < HEADER_SIZE) return false
        val header = ByteArray(HEADER_SIZE)
        return file.inputStream().use { stream ->
            val read = stream.read(header)
            read == HEADER_SIZE && isPlaintext(header)
        }
    }
}
