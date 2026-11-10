package com.example.lifetrack.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件的认证加密：PBKDF2-HMAC-SHA256 派生 256 位密钥，AES-GCM 提供机密性与完整性。
 *
 * 设计取舍：
 * - 纯 JVM 实现（不含 Android 类），加密与解密路径都能被 JVM 单测真实覆盖，而不是只靠仪器测试。
 * - 口令不落盘、不进日志：本类只接收调用方传入的 `CharArray`，用完由调用方清零。
 * - 迭代次数带上下界：太低等于没加固，太高会让恶意文件把恢复流程拖死。
 * - GCM 认证失败无法区分「口令错」与「密文被改」，对外统一按「口令错误或文件损坏」提示，不猜。
 */
object BackupCrypto {

    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val CIPHER_NAME = "AES-256-GCM"
    const val TRANSFORMATION = "AES/GCM/NoPadding"

    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 600_000

    const val SALT_LENGTH = 16
    const val IV_LENGTH = 12
    const val KEY_LENGTH_BITS = 256
    const val GCM_TAG_LENGTH = 16

    /**
     * 加密前的明文上限 5 MiB：Base64 后约 6.8 MiB，加上头部仍小于 [com.example.lifetrack.core.backup.BackupIo.MAX_BYTES]，
     * 避免出现「本应用导出的文件，本应用自己读不了」。
     */
    const val MAX_PLAINTEXT_BYTES = 5 * 1024 * 1024

    private const val GCM_TAG_BITS = GCM_TAG_LENGTH * 8

    private val random = SecureRandom()

    enum class Reason {
        EMPTY_PASSPHRASE,
        INVALID_ITERATIONS,
        INVALID_SALT,
        INVALID_IV,
        CIPHERTEXT_TOO_SHORT,
        PLAINTEXT_TOO_LARGE,
        DECRYPT_FAILED,
        UNAVAILABLE
    }

    /** 加密层失败。调用方负责映射成用户可读文案，不把异常消息直接展示给用户。 */
    class Failure(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause)

    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also(random::nextBytes)

    fun newIv(): ByteArray = ByteArray(IV_LENGTH).also(random::nextBytes)

    fun seal(
        plaintext: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int,
        aad: ByteArray
    ): ByteArray {
        validate(passphrase, salt, iv, iterations)
        if (plaintext.size > MAX_PLAINTEXT_BYTES) throw Failure(Reason.PLAINTEXT_TOO_LARGE)

        val key = deriveKey(passphrase, salt, iterations)
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION)
        } catch (e: GeneralSecurityException) {
            throw Failure(Reason.UNAVAILABLE, e)
        }
        return try {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(plaintext)
        } catch (e: GeneralSecurityException) {
            throw Failure(Reason.UNAVAILABLE, e)
        }
    }

    fun open(
        ciphertext: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int,
        aad: ByteArray
    ): ByteArray {
        validate(passphrase, salt, iv, iterations)
        if (ciphertext.size < GCM_TAG_LENGTH) throw Failure(Reason.CIPHERTEXT_TOO_SHORT)

        val key = deriveKey(passphrase, salt, iterations)
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION)
        } catch (e: GeneralSecurityException) {
            throw Failure(Reason.UNAVAILABLE, e)
        }
        return try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            // GCM 标签校验失败：口令错误或密文/头部被改动，二者在本层不可区分。
            throw Failure(Reason.DECRYPT_FAILED, e)
        }
    }

    private fun validate(passphrase: CharArray, salt: ByteArray, iv: ByteArray, iterations: Int) {
        if (passphrase.isEmpty() || passphrase.all { it.isWhitespace() }) {
            throw Failure(Reason.EMPTY_PASSPHRASE)
        }
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw Failure(Reason.INVALID_ITERATIONS)
        }
        if (salt.size != SALT_LENGTH) throw Failure(Reason.INVALID_SALT)
        if (iv.size != IV_LENGTH) throw Failure(Reason.INVALID_IV)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(KDF_ALGORITHM)
                .generateSecret(spec)
                .encoded
            SecretKeySpec(encoded, "AES")
        } catch (e: GeneralSecurityException) {
            throw Failure(Reason.UNAVAILABLE, e)
        } finally {
            spec.clearPassword()
        }
    }
}
