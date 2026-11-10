package com.example.lifetrack.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 里不可导出的 AES-256 主密钥封装数据库口令。
 *
 * 设计口径见 `docs/security-hardening-confirmation.md` §3 D1/D3：
 * - 主密钥**不绑定生物识别**：绑定后冷启动、后台与备份都要先解锁，且新增指纹会让密钥永久失效（等于数据全丢）。生物识别只用于应用层锁。
 * - 每次加密由系统生成随机 IV，信封存 IV 供解密使用；口令本身只在内存里短暂停留。
 * - 密钥或信封失效时明确抛错，**绝不静默新建空库**。
 */
class DatabaseKeyStore(private val alias: String = DEFAULT_ALIAS) {

    enum class Failure {
        /** Keystore 里没有主密钥（常见于清除应用数据后数据库文件仍在）。 */
        KEY_MISSING,

        /** 信封结构损坏或被截断。 */
        INVALID_ENVELOPE,

        /** 认证标签校验失败：信封被篡改，或主密钥已更换。 */
        DECRYPT_FAILED,
        KEYSTORE_ERROR
    }

    class KeyUnavailableException(val failure: Failure, cause: Throwable? = null) :
        Exception("database key unavailable: $failure", cause)

    companion object {
        const val DEFAULT_ALIAS = "lifetrack_db_master_key"
        const val PASSPHRASE_LENGTH = PassphraseEnvelope.PASSPHRASE_LENGTH
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }

    /**
     * 生成一条新的随机数据库口令并封装。
     *
     * @return 信封文本（可持久化）与本次使用的口令（仅供当次打开数据库，用完必须 wipe）。
     */
    fun sealNewPassphrase(): Pair<String, ByteArray> {
        val passphrase = ByteArray(PASSPHRASE_LENGTH).also { SecureRandom().nextBytes(it) }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(passphrase)
            PassphraseEnvelope.encode(cipher.iv, ciphertext) to passphrase
        } catch (e: Exception) {
            passphrase.fill(0)
            throw KeyUnavailableException(Failure.KEYSTORE_ERROR, e)
        }
    }

    /** 还原口令；任何异常都带可判定的 [Failure]，便于 UI 给出准确的下一步指引。 */
    fun unseal(envelopeText: String): ByteArray {
        val sealed = PassphraseEnvelope.decode(envelopeText)
            ?: throw KeyUnavailableException(Failure.INVALID_ENVELOPE)
        return try {
            val key = loadKey() ?: throw KeyUnavailableException(Failure.KEY_MISSING)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, sealed.iv))
            cipher.doFinal(sealed.ciphertext)
        } catch (e: KeyUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw KeyUnavailableException(Failure.DECRYPT_FAILED, e)
        }
    }

    fun hasKey(): Boolean = try {
        loadKey() != null
    } catch (e: KeyUnavailableException) {
        false
    }

    private fun loadKey(): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (!store.containsAlias(alias)) return null
        return store.getKey(alias, null) as? SecretKey
            ?: throw KeyUnavailableException(Failure.KEYSTORE_ERROR)
    }

    private fun getOrCreateKey(): SecretKey {
        loadKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        generator.generateKey()
        return loadKey() ?: throw KeyUnavailableException(Failure.KEYSTORE_ERROR)
    }
}
