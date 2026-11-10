package com.example.lifetrack.core.security

import java.util.Base64

/**
 * 数据库口令经 Keystore 主密钥封装后的信封：`IV + AES-GCM 密文`，序列化成单行文本存 DataStore。
 *
 * 本类只做**结构**校验（格式、长度、Base64）：完整性由 AES-GCM 的认证标签在解密时保证，
 * 参见 [DatabaseKeyStore]。信封里永远不会出现口令明文。
 */
object PassphraseEnvelope {

    const val IV_LENGTH = 12
    const val GCM_TAG_LENGTH = 16
    const val PASSPHRASE_LENGTH = 32

    /** 32 字节口令 + 16 字节认证标签，是合法密文的最小长度。 */
    const val MIN_CIPHERTEXT_LENGTH = GCM_TAG_LENGTH + PASSPHRASE_LENGTH

    private const val PREFIX = "lt-enc-v1:"

    data class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

    fun encode(iv: ByteArray, ciphertext: ByteArray): String {
        require(iv.size == IV_LENGTH) { "IV must be exactly $IV_LENGTH bytes" }
        require(ciphertext.size >= MIN_CIPHERTEXT_LENGTH) { "ciphertext is shorter than passphrase + tag" }
        return PREFIX + encode64(iv) + ":" + encode64(ciphertext)
    }

    /** 结构不合法时返回 null，由调用方映射成 [DatabaseKeyStore.Failure.INVALID_ENVELOPE]。 */
    fun decode(text: String): Sealed? {
        if (!text.startsWith(PREFIX)) return null
        val parts = text.removePrefix(PREFIX).split(':')
        if (parts.size != 2) return null
        val iv = decode64(parts[0]) ?: return null
        val ciphertext = decode64(parts[1]) ?: return null
        if (iv.size != IV_LENGTH) return null
        if (ciphertext.size < MIN_CIPHERTEXT_LENGTH) return null
        return Sealed(iv, ciphertext)
    }

    private fun encode64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode64(text: String): ByteArray? =
        try {
            Base64.getDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
}
