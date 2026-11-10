package com.example.lifetrack.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 密钥材料单独放一份 DataStore，不与主题等普通设置混在一起，便于隐私审查时定位。 */
private val Context.securityDataStore by preferencesDataStore(name = "life_track_security")

/**
 * 信封的持久化与「取口令」编排。
 *
 * 这里只存密文信封；口令明文不落盘。调用方拿到口令打开数据库后应尽快 [wipe]。
 */
class DatabaseKeyRepository(private val context: Context) {

    private val envelopeKey = stringPreferencesKey("db_passphrase_envelope")
    private val mutex = Mutex()

    suspend fun readEnvelope(): String? = context.securityDataStore.data.first()[envelopeKey]

    suspend fun writeEnvelope(envelope: String) {
        context.securityDataStore.edit { preferences ->
            preferences[envelopeKey] = envelope
        }
    }

    suspend fun clearEnvelope() {
        context.securityDataStore.edit { preferences ->
            preferences.remove(envelopeKey)
        }
    }

    /** 已有信封则解封；全新安装则生成口令并封装。串行化避免并发时生成两份互不相认的口令。 */
    suspend fun getOrCreatePassphrase(keyStore: DatabaseKeyStore): ByteArray = mutex.withLock {
        readEnvelope()?.let { return@withLock keyStore.unseal(it) }
        val (envelope, passphrase) = keyStore.sealNewPassphrase()
        writeEnvelope(envelope)
        passphrase
    }

    companion object {
        fun wipe(passphrase: ByteArray) {
            passphrase.fill(0)
        }
    }
}
