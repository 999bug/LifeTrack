package com.example.lifetrack.feature.settings.backup

import com.example.lifetrack.core.backup.BackupDecodeResult
import com.example.lifetrack.core.backup.BackupError
import com.example.lifetrack.core.backup.BackupIo
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.backup.BackupReencryptResult
import com.example.lifetrack.core.security.BackupCrypto
import com.example.lifetrack.core.security.RetryBackoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 设置页备份流程依赖的最小操作集，便于用 fake 实现覆盖状态机。 */
interface BackupOperations {
    suspend fun buildBackupJson(passphrase: CharArray?): String
    suspend fun decodeBackupJson(text: String, passphrase: CharArray? = null): BackupDecodeResult
    suspend fun reencryptBackup(
        text: String,
        oldPassphrase: CharArray,
        newPassphrase: CharArray
    ): BackupReencryptResult

    suspend fun restoreBackup(payload: BackupPayload)
    suspend fun countAllRecords(): Int
}

data class BackupUiState(
    val busy: Boolean = false,
    val status: String? = null,
    val failure: String? = null,
    val pendingRestore: BackupPayload? = null,
    val pendingRestoreCount: Int = 0,
    val currentRecordCount: Int = 0,
    val pendingEncryptedText: String? = null,
    val importErrorMessage: String? = null,
    val importFailures: Int = 0,
    val importCooldownSeconds: Int = 0,
    val rekeySourceText: String? = null,
    val pendingRekeyText: String? = null,
    val rekeyErrorMessage: String? = null,
    val rekeyFailures: Int = 0,
    val rekeyCooldownSeconds: Int = 0
)

/**
 * 备份导出入、恢复、换口令的状态机。
 *
 * 文件选择器和口令输入仍留在 Compose；读写 URI 由调用方通过回调传入，
 * 本类只负责业务编排、错误映射、危险动作确认所需的数据和失败节流。
 */
class BackupController(
    private val scope: CoroutineScope,
    private val operations: BackupOperations
) {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun export(passphrase: CharArray?, writeText: suspend (String) -> Unit) {
        if (_state.value.busy) return
        scope.launch {
            begin()
            try {
                val text = operations.buildBackupJson(passphrase)
                writeText(text)
                val count = operations.countAllRecords()
                _state.update {
                    it.copy(
                        status = if (passphrase == null) {
                            "已导出 $count 条明文记录。"
                        } else {
                            "已导出 $count 条加密记录，请牢记口令。"
                        }
                    )
                }
            } catch (e: BackupCrypto.Failure) {
                _state.update { it.copy(failure = "导出失败：${e.reason.exportMessage()}") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(failure = "导出失败：${e.message ?: "无法写入所选位置"}") }
            } finally {
                passphrase?.fill('\u0000')
                finish()
            }
        }
    }

    fun loadRestoreText(text: String?) {
        if (_state.value.busy) return
        scope.launch {
            begin()
            if (text == null) {
                _state.update { it.copy(failure = "无法读取文件，或文件超过 ${BackupIo.MAX_BYTES / 1024 / 1024} MB。") }
                finish()
                return@launch
            }
            when (val result = operations.decodeBackupJson(text)) {
                is BackupDecodeResult.Success -> showPendingRestore(result.payload)
                is BackupDecodeResult.Invalid -> _state.update {
                    it.copy(failure = describeBackupError(result.error, result.detail))
                }
                BackupDecodeResult.PassphraseRequired -> _state.update {
                    it.copy(
                        pendingEncryptedText = text,
                        importErrorMessage = null,
                        importFailures = 0,
                        importCooldownSeconds = 0
                    )
                }
            }
            finish()
        }
    }

    fun submitRestorePassphrase(passphrase: CharArray) {
        val text = _state.value.pendingEncryptedText ?: return
        if (_state.value.busy || _state.value.importCooldownSeconds > 0) return
        scope.launch {
            begin()
            try {
                when (val result = operations.decodeBackupJson(text, passphrase)) {
                    is BackupDecodeResult.Success -> showPendingRestore(result.payload)
                    is BackupDecodeResult.Invalid -> {
                        val failures = _state.value.importFailures + 1
                        _state.update {
                            it.copy(
                                importErrorMessage = describeBackupError(result.error, result.detail),
                                importFailures = failures,
                                importCooldownSeconds = if (result.error == BackupError.WRONG_PASSPHRASE) {
                                    RetryBackoff.secondsAfter(failures)
                                } else {
                                    it.importCooldownSeconds
                                }
                            )
                        }
                    }
                    BackupDecodeResult.PassphraseRequired -> Unit
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(importErrorMessage = "解密失败：${e.message ?: "无法读取备份"}") }
            } finally {
                passphrase.fill('\u0000')
                finish()
            }
        }
    }

    fun confirmRestore() {
        val payload = _state.value.pendingRestore ?: return
        if (_state.value.busy) return
        scope.launch {
            begin()
            val count = _state.value.pendingRestoreCount
            try {
                operations.restoreBackup(payload)
                _state.update {
                    it.copy(
                        pendingRestore = null,
                        pendingRestoreCount = 0,
                        currentRecordCount = 0,
                        status = "已恢复 $count 条记录。"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(failure = "恢复失败：${e.message ?: "数据库写入异常"}") }
            } finally {
                finish()
            }
        }
    }

    fun cancelRestore() {
        _state.update {
            it.copy(
                pendingRestore = null,
                pendingRestoreCount = 0,
                currentRecordCount = 0,
                pendingEncryptedText = null,
                importErrorMessage = null,
                importFailures = 0,
                importCooldownSeconds = 0
            )
        }
    }

    fun clearImportError() {
        _state.update { it.copy(importErrorMessage = null) }
    }

    fun setImportError(message: String) {
        _state.update { it.copy(importErrorMessage = message) }
    }

    fun loadRekeyText(text: String?) {
        if (_state.value.busy) return
        scope.launch {
            begin()
            if (text == null) {
                _state.update { it.copy(failure = "无法读取文件，或文件超过 ${BackupIo.MAX_BYTES / 1024 / 1024} MB。") }
                finish()
                return@launch
            }
            when (val result = operations.decodeBackupJson(text)) {
                is BackupDecodeResult.Success ->
                    _state.update { it.copy(failure = "这是未加密的备份，没有口令可以更换。") }

                is BackupDecodeResult.Invalid ->
                    _state.update { it.copy(failure = describeBackupError(result.error, result.detail)) }

                BackupDecodeResult.PassphraseRequired -> _state.update {
                    it.copy(
                        rekeySourceText = text,
                        rekeyErrorMessage = null,
                        rekeyFailures = 0,
                        rekeyCooldownSeconds = 0
                    )
                }
            }
            finish()
        }
    }

    fun submitRekey(oldPassphrase: CharArray, newPassphrase: CharArray) {
        val source = _state.value.rekeySourceText ?: return
        if (_state.value.busy || _state.value.rekeyCooldownSeconds > 0) return
        scope.launch {
            begin()
            try {
                when (val result = operations.reencryptBackup(source, oldPassphrase, newPassphrase)) {
                    is BackupReencryptResult.Success -> _state.update {
                        it.copy(
                            rekeySourceText = null,
                            pendingRekeyText = result.text,
                            rekeyErrorMessage = null,
                            rekeyFailures = 0,
                            rekeyCooldownSeconds = 0
                        )
                    }
                    is BackupReencryptResult.Invalid -> {
                        val failures = _state.value.rekeyFailures + 1
                        _state.update {
                            it.copy(
                                rekeyErrorMessage = describeBackupError(result.error, result.detail),
                                rekeyFailures = failures,
                                rekeyCooldownSeconds = if (result.error == BackupError.WRONG_PASSPHRASE) {
                                    RetryBackoff.secondsAfter(failures)
                                } else {
                                    it.rekeyCooldownSeconds
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(rekeyErrorMessage = "更换失败：${e.message ?: "加密异常"}") }
            } finally {
                oldPassphrase.fill('\u0000')
                newPassphrase.fill('\u0000')
                finish()
            }
        }
    }

    fun saveRekeyed(writeText: suspend (String) -> Unit) {
        val text = _state.value.pendingRekeyText ?: return
        if (_state.value.busy) return
        scope.launch {
            begin()
            try {
                writeText(text)
                _state.update {
                    it.copy(
                        pendingRekeyText = null,
                        status = "已生成新口令的备份。确认能恢复后，再删除旧文件。"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(
                        pendingRekeyText = null,
                        failure = "写入失败：${e.message ?: "无法写入所选位置"}"
                    )
                }
            } finally {
                finish()
            }
        }
    }

    fun cancelRekey() {
        _state.update {
            it.copy(
                rekeySourceText = null,
                pendingRekeyText = null,
                rekeyErrorMessage = null,
                rekeyFailures = 0,
                rekeyCooldownSeconds = 0
            )
        }
    }

    fun clearRekeyError() {
        _state.update { it.copy(rekeyErrorMessage = null) }
    }

    fun setRekeyError(message: String) {
        _state.update { it.copy(rekeyErrorMessage = message) }
    }

    fun tickCooldowns() {
        _state.update {
            it.copy(
                importCooldownSeconds = (it.importCooldownSeconds - 1).coerceAtLeast(0),
                rekeyCooldownSeconds = (it.rekeyCooldownSeconds - 1).coerceAtLeast(0)
            )
        }
    }

    fun clearFailure() {
        _state.update { it.copy(failure = null) }
    }

    private suspend fun showPendingRestore(payload: BackupPayload) {
        val currentCount = operations.countAllRecords()
        _state.update {
            it.copy(
                pendingRestore = payload,
                pendingRestoreCount = payload.events.size,
                currentRecordCount = currentCount,
                pendingEncryptedText = null,
                importErrorMessage = null,
                importFailures = 0,
                importCooldownSeconds = 0
            )
        }
    }

    private fun begin() {
        _state.update { it.copy(busy = true, status = null, failure = null) }
    }

    private fun finish() {
        _state.update { it.copy(busy = false) }
    }
}

private fun describeBackupError(error: BackupError, detail: String?): String = buildString {
    append(error.message())
    val technical = error == BackupError.INVALID_ENVELOPE ||
        error == BackupError.UNSUPPORTED_ENCRYPTION ||
        error == BackupError.UNSUPPORTED_VERSION
    if (!technical) detail?.let { append("（记录 $it）") }
}

private fun BackupError.message(): String = when (this) {
    BackupError.NOT_JSON -> "文件不是有效的 JSON。"
    BackupError.NOT_LIFETRACK_FILE -> "这不是「生活记」导出的备份文件。"
    BackupError.UNSUPPORTED_VERSION -> "备份版本不受支持，请用相同或更新版本的应用恢复。"
    BackupError.TOO_MANY_EVENTS -> "备份中的记录条数超出上限。"
    BackupError.EVENT_COUNT_MISMATCH -> "备份声明的条数与实际内容不一致，文件可能被改动过。"
    BackupError.DUPLICATE_EVENT_ID -> "备份中存在重复的记录 ID。"
    BackupError.UNKNOWN_TYPE -> "备份中包含当前版本不认识的记录类型。"
    BackupError.MISSING_BASE_TYPE -> "备份缺少内置的记录类型。"
    BackupError.INVALID_ZONE -> "记录的时区标识无效。"
    BackupError.INVALID_LOCAL_DATE -> "记录的本地日期格式无效。"
    BackupError.LOCAL_DATE_MISMATCH -> "记录的本地日期与时间戳不一致。"
    BackupError.INVALID_TIMESTAMP -> "记录的时间戳超出合理范围。"
    BackupError.INVALID_BRISTOL -> "布里斯托类型超出 1–7。"
    BackupError.INVALID_AMOUNT -> "排便量取值超出范围。"
    BackupError.INVALID_EFFORT -> "费力程度取值超出范围。"
    BackupError.INVALID_DURATION -> "排便用时不合法：应在 1–60 分钟，且结束时间不能早于开始时间。"
    BackupError.UNSUPPORTED_DETAIL_TYPE -> "备份包含当前版本尚不支持的记录类型详情，请升级应用后再恢复。为避免丢失数据，已拒绝导入。"
    BackupError.DETAIL_TYPE_MISMATCH -> "记录的详情类型与事件类型不一致。"
    BackupError.INVALID_DETAIL_FIELD -> "记录的详情字段不合法或取值超出范围。"
    BackupError.DETAIL_NOT_ALLOWED -> "备份中的详情字段与记录类型或备份版本不匹配。"
    BackupError.WRONG_PASSPHRASE -> "口令错误，或备份文件已损坏、被改动过。请确认口令后重试；口令区分大小写和全半角。"
    BackupError.INVALID_ENVELOPE -> "加密备份的结构不完整或已损坏，无法解密。"
    BackupError.UNSUPPORTED_ENCRYPTION -> "备份使用了当前版本不支持的加密参数，请升级应用后再恢复。"
    BackupError.EMPTY_PASSPHRASE -> "请输入备份口令。"
    BackupError.BACKUP_TOO_LARGE -> "记录过多，加密后的备份会超过大小上限。"
    BackupError.NOT_ENCRYPTED_BACKUP -> "这是未加密的备份，没有口令可以更换。"
}

private fun BackupCrypto.Reason.exportMessage(): String = when (this) {
    BackupCrypto.Reason.EMPTY_PASSPHRASE -> "口令不能为空。"
    BackupCrypto.Reason.PLAINTEXT_TOO_LARGE -> "记录过多，加密后的文件超过 ${BackupIo.MAX_BYTES / 1024 / 1024} MB 上限。"
    BackupCrypto.Reason.UNAVAILABLE -> "当前设备不支持所需的加密算法。"
    BackupCrypto.Reason.INVALID_ITERATIONS,
    BackupCrypto.Reason.INVALID_SALT,
    BackupCrypto.Reason.INVALID_IV,
    BackupCrypto.Reason.CIPHERTEXT_TOO_SHORT,
    BackupCrypto.Reason.DECRYPT_FAILED -> "加密失败，请重试。"
}
