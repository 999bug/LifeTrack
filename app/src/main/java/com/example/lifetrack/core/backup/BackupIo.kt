package com.example.lifetrack.core.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** 备份文件的读写边界。读入设上限，避免用户误选一个超大文件把内存打满。 */
object BackupIo {

    const val MAX_BYTES = 8 * 1024 * 1024

    /** 超过 [limit] 字节返回 null，由调用方提示「文件过大」。 */
    fun readUtf8(input: InputStream, limit: Int = MAX_BYTES): String? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
        return String(buffer.toByteArray(), Charsets.UTF_8)
    }

    fun writeUtf8(output: OutputStream, text: String) {
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
