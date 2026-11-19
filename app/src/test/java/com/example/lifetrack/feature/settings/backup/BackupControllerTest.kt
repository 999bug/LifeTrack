package com.example.lifetrack.feature.settings.backup

import com.example.lifetrack.core.backup.BackupDecodeResult
import com.example.lifetrack.core.backup.BackupError
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.backup.BackupReencryptResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun encryptedImportWaitsForPassphrase() {
        val controller = BackupController(scope, FakeOperations())

        controller.loadRestoreText("encrypted")

        assertEquals("encrypted", controller.state.value.pendingEncryptedText)
        assertNull(controller.state.value.pendingRestore)
    }

    @Test
    fun wrongImportPassphraseStartsCooldown() {
        val operations = FakeOperations(
            decodeResults = mutableListOf(
                BackupDecodeResult.PassphraseRequired,
                BackupDecodeResult.Invalid(BackupError.WRONG_PASSPHRASE),
                BackupDecodeResult.Invalid(BackupError.WRONG_PASSPHRASE),
                BackupDecodeResult.Invalid(BackupError.WRONG_PASSPHRASE)
            )
        )
        val controller = BackupController(scope, operations)
        controller.loadRestoreText("encrypted")

        repeat(3) {
            controller.submitRestorePassphrase("wrong".toCharArray())
        }

        assertTrue(controller.state.value.importCooldownSeconds > 0)
        assertTrue(controller.state.value.importErrorMessage?.isNotBlank() == true)
        assertEquals(3, controller.state.value.importFailures)
    }

    @Test
    fun successfulRekeyPublishesNewBackupText() {
        val operations = FakeOperations(
            rekeyResult = BackupReencryptResult.Success("new-backup")
        )
        val controller = BackupController(scope, operations)
        controller.loadRekeyText("encrypted")

        controller.submitRekey(
            oldPassphrase = "old".toCharArray(),
            newPassphrase = "new-passphrase".toCharArray()
        )

        assertEquals("new-backup", controller.state.value.pendingRekeyText)
        assertNull(controller.state.value.rekeySourceText)
        assertNull(controller.state.value.rekeyErrorMessage)
    }

    @Test
    fun exportWritesEncryptedBackupAndReportsCount() {
        val operations = FakeOperations(exportText = "backup-json", recordCount = 3)
        val controller = BackupController(scope, operations)
        var written: String? = null

        controller.export("passphrase".toCharArray()) { text -> written = text }

        assertEquals("backup-json", written)
        assertEquals("已导出 3 条加密记录，请牢记口令。", controller.state.value.status)
    }

    private class FakeOperations(
        private val decodeResults: MutableList<BackupDecodeResult> = mutableListOf(
            BackupDecodeResult.PassphraseRequired
        ),
        private val rekeyResult: BackupReencryptResult = BackupReencryptResult.Success("rekeyed"),
        private val exportText: String = "{}",
        private val recordCount: Int = 1
    ) : BackupOperations {
        override suspend fun buildBackupJson(passphrase: CharArray?): String = exportText

        override suspend fun decodeBackupJson(
            text: String,
            passphrase: CharArray?
        ): BackupDecodeResult =
            if (decodeResults.size > 1) decodeResults.removeAt(0) else decodeResults.first()

        override suspend fun reencryptBackup(
            text: String,
            oldPassphrase: CharArray,
            newPassphrase: CharArray
        ): BackupReencryptResult = rekeyResult

        override suspend fun restoreBackup(payload: BackupPayload) = Unit

        override suspend fun countAllRecords(): Int = recordCount
    }
}
