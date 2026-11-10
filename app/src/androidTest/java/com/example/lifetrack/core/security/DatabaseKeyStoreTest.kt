package com.example.lifetrack.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** 在真实 Android Keystore 上验证主密钥的生成、封装与失效路径。 */
@RunWith(AndroidJUnit4::class)
class DatabaseKeyStoreTest {

    private val alias = "lifetrack_test_db_master_key"
    private lateinit var store: DatabaseKeyStore

    @Before
    fun setUp() {
        deleteAlias()
        store = DatabaseKeyStore(alias)
    }

    @After
    fun tearDown() {
        deleteAlias()
    }

    @Test
    fun sealThenUnseal_returnsSamePassphrase() {
        val (envelope, passphrase) = store.sealNewPassphrase()
        val restored = store.unseal(envelope)
        assertArrayEquals(passphrase, restored)
        assertEquals(PassphraseEnvelope.MIN_CIPHERTEXT_LENGTH, PassphraseEnvelope.decode(envelope)!!.ciphertext.size)
    }

    @Test
    fun hasKey_tracksKeyCreation() {
        assertFalse(store.hasKey())
        store.sealNewPassphrase()
        assertTrue(store.hasKey())
    }

    @Test
    fun unseal_rejectsMalformedEnvelope() {
        val failure = runCatching { store.unseal("not-an-envelope") }
            .exceptionOrNull() as? DatabaseKeyStore.KeyUnavailableException
        assertEquals(DatabaseKeyStore.Failure.INVALID_ENVELOPE, failure?.failure)
    }

    @Test
    fun unseal_detectsTamperedCiphertext() {
        val (envelope, _) = store.sealNewPassphrase()
        val sealed = PassphraseEnvelope.decode(envelope)!!
        val tampered = sealed.ciphertext.clone().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        val badEnvelope = PassphraseEnvelope.encode(sealed.iv, tampered)

        val failure = runCatching { store.unseal(badEnvelope) }
            .exceptionOrNull() as? DatabaseKeyStore.KeyUnavailableException
        assertEquals(DatabaseKeyStore.Failure.DECRYPT_FAILED, failure?.failure)
    }

    @Test
    fun unseal_withoutKey_reportsKeyMissing() {
        val (envelope, _) = store.sealNewPassphrase()
        deleteAlias()

        val failure = runCatching { store.unseal(envelope) }
            .exceptionOrNull() as? DatabaseKeyStore.KeyUnavailableException
        assertEquals(DatabaseKeyStore.Failure.KEY_MISSING, failure?.failure)
    }

    private fun deleteAlias() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }
}
