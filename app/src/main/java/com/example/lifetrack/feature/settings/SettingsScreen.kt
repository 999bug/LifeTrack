package com.example.lifetrack.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.BuildConfig
import com.example.lifetrack.core.backup.BackupCodec
import com.example.lifetrack.core.backup.BackupDecodeResult
import com.example.lifetrack.core.backup.BackupIo
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.backup.BackupReencryptResult
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.AppLockTimeout
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.TeaFields
import com.example.lifetrack.core.model.ThemeMode
import com.example.lifetrack.core.security.PassphraseQuality
import com.example.lifetrack.core.security.SensitiveTypes
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.lock.AppLockViewModel
import com.example.lifetrack.feature.lock.canUseAppLock
import com.example.lifetrack.feature.settings.backup.BackupController
import com.example.lifetrack.feature.settings.backup.BackupOperations
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val backupStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

/**
 * 设置页：按「使用频率 × 破坏性」分层，不再一段功能一张卡平铺。
 *
 * - 无标题主卡：记录项目、外观——进设置最常做的两件事。
 * - 安全：中频且状态需随时可查，保持独立。
 * - 数据与备份：低频且高破坏，默认折叠；进行中 / 完成 / 失败时自动展开。
 * - 关于：更新日志、开源许可、数据与隐私，以及应用更新（P2：不再是独立卡）。
 * - 页脚：版本号与免责文案，沉到页面最后一段。
 *
 * 记录项目管理在独立页（[ModuleManagementScreen]）；更新状态机仍由
 * [UpdateSection] 自己维护，这里只决定它挂在哪一段。
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    appLockViewModel: AppLockViewModel,
    updateViewModel: UpdateViewModel,
    onOpenModules: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val updateState by updateViewModel.updateState.collectAsStateWithLifecycle()
    val trackTypes by viewModel.trackTypes.collectAsStateWithLifecycle()
    val appLockEnabled by appLockViewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appLockTimeout by appLockViewModel.appLockTimeout.collectAsStateWithLifecycle()
    val compactState by viewModel.compactState.collectAsStateWithLifecycle()
    val customCoffeeVarieties by viewModel.customCoffeeVarieties.collectAsStateWithLifecycle()
    val customTeaVarieties by viewModel.customTeaVarieties.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupOperations = remember(viewModel) {
        object : BackupOperations {
            override suspend fun buildBackupJson(passphrase: CharArray?): String =
                viewModel.buildBackupJson(passphrase)

            override suspend fun decodeBackupJson(
                text: String,
                passphrase: CharArray?
            ): BackupDecodeResult = viewModel.decodeBackupJson(text, passphrase)

            override suspend fun reencryptBackup(
                text: String,
                oldPassphrase: CharArray,
                newPassphrase: CharArray
            ): BackupReencryptResult = viewModel.reencryptBackup(text, oldPassphrase, newPassphrase)

            override suspend fun restoreBackup(payload: BackupPayload) =
                viewModel.restoreBackup(payload)

            override suspend fun countAllRecords(): Int = viewModel.countAllRecords()
        }
    }
    val backupController = remember(scope, backupOperations) {
        BackupController(scope, backupOperations)
    }
    val backupState by backupController.state.collectAsStateWithLifecycle()
    val busy = backupState.busy
    val status = backupState.status
    val failure = backupState.failure
    val pending = backupState.pendingRestore
    val pendingCount = backupState.pendingRestoreCount
    val currentCount = backupState.currentRecordCount
    val pendingEncryptedText = backupState.pendingEncryptedText
    val importPassphraseError = backupState.importErrorMessage
    val importCooldown = backupState.importCooldownSeconds
    val rekeySourceText = backupState.rekeySourceText
    val rekeyError = backupState.rekeyErrorMessage
    val rekeyCooldown = backupState.rekeyCooldownSeconds

    var privacyExpanded by remember { mutableStateOf(false) }
    // 数据与备份默认收起；低频项不持久化展开态，下次进来仍是一行。
    var dataExpanded by remember { mutableStateOf(false) }
    var themeDialogVisible by remember { mutableStateOf(false) }
    var varietiesDialogVisible by remember { mutableStateOf(false) }
    var timeoutDialogVisible by remember { mutableStateOf(false) }
    var appLockError by remember { mutableStateOf<String?>(null) }
    var licensesVisible by remember { mutableStateOf(false) }
    var changelogVisible by remember { mutableStateOf(false) }

    // 导出：先选加密方式；加密要两次口令，明文要过一道风险确认。
    var exportChoiceVisible by remember { mutableStateOf(false) }
    var exportAsEncrypted by remember { mutableStateOf(true) }
    var exportPassphrase by remember { mutableStateOf("") }
    var exportPassphraseConfirm by remember { mutableStateOf("") }
    var exportPassphraseError by remember { mutableStateOf<String?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var plainExportWarningVisible by remember { mutableStateOf(false) }

    // 恢复：加密备份先读文本，拿到口令后再解一次。
    var importPassphrase by remember { mutableStateOf("") }

    // 更换备份口令：选一份加密备份，解旧 → 用新口令重加密 → 另存为新文件。
    var rekeyOldPassphrase by remember { mutableStateOf("") }
    var rekeyNewPassphrase by remember { mutableStateOf("") }
    var rekeyNewPassphraseConfirm by remember { mutableStateOf("") }

    LaunchedEffect(importCooldown, rekeyCooldown) {
        if (importCooldown > 0 || rekeyCooldown > 0) {
            delay(1_000)
            backupController.tickCooldowns()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val passphrase = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri == null) {
            passphrase?.fill('\u0000')
            return@rememberLauncherForActivityResult
        }
        backupController.export(passphrase) { text ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    BackupIo.writeUtf8(output, text)
                } ?: error("无法写入所选位置")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BackupIo.readUtf8(it) }
                }.getOrNull()
            }
            backupController.loadRestoreText(text)
        }
    }

    val rekeyPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BackupIo.readUtf8(it) }
                }.getOrNull()
            }
            backupController.loadRekeyText(text)
        }
    }

    val rekeySaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            backupController.cancelRekey()
            return@rememberLauncherForActivityResult
        }
        backupController.saveRekeyed { text ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    BackupIo.writeUtf8(output, text)
                } ?: error("无法写入所选位置")
            }
        }
    }

    LaunchedEffect(backupState.pendingRekeyText) {
        if (backupState.pendingRekeyText != null) {
            rekeySaveLauncher.launch(backupFileName(suffix = "rekeyed"))
        }
    }

    // 状态外溢：导出 / 恢复进行中、刚完成或失败时自动展开，不让状态藏在折叠区里。
    // 用一次性置位而不是「expanded || busy」，用户随后仍能手动收起。
    LaunchedEffect(busy, status, failure) {
        if (busy || status != null || failure != null) dataExpanded = true
    }
    // 有可下载的新版本时，首屏「更新日志」行同步标记，避免更新入口沉到页脚后被错过。
    val availableVersion = (updateState as? UpdateRepository.Status.Available)?.release?.version

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 平板 / 横屏下限制正文宽度并居中，避免一行文字横跨 1200dp
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        PrimarySection(
            trackTypes = trackTypes,
            themeMode = themeMode,
            onOpenModules = onOpenModules,
            onOpenVarieties = { varietiesDialogVisible = true },
            onOpenTheme = { themeDialogVisible = true }
        )

        SettingsSection(title = "安全") {
            SettingsRow(
                title = "应用锁",
                icon = Icons.Filled.Lock,
                subtitle = when (appLockEnabled) {
                    true -> "已开启 · 切到后台后需验证"
                    false -> "未开启 · 打开应用时不需要验证"
                    null -> "正在读取设置…"
                },
                role = Role.Switch,
                onClick = {
                    val enable = appLockEnabled != true
                    if (enable && !canUseAppLock(context)) {
                        appLockError = "请先在系统设置中设置锁屏密码（PIN、图案或密码），再开启应用锁。"
                    } else {
                        appLockViewModel.setAppLockEnabled(enable)
                    }
                },
                trailing = {
                    Switch(checked = appLockEnabled == true, onCheckedChange = null)
                }
            )
            if (appLockEnabled == true) {
                SettingsDivider()
                SettingsRow(
                    title = "自动锁定",
                    subtitle = appLockTimeout.label(),
                    onClick = { timeoutDialogVisible = true },
                    trailing = { ChevronTrailing() }
                )
            }
        }

        CollapsibleSettingsSection(
            title = "数据与备份",
            subtitle = "导出、恢复与更换口令",
            icon = Icons.Filled.Storage,
            expanded = dataExpanded,
            onToggle = { dataExpanded = !dataExpanded }
        ) {
            SettingsRow(
                title = "导出备份",
                icon = Icons.Filled.FileUpload,
                subtitle = "默认加密导出，恢复时需要口令",
                enabled = !busy,
                onClick = {
                    exportAsEncrypted = true
                    exportPassphrase = ""
                    exportPassphraseConfirm = ""
                    exportPassphraseError = null
                    exportChoiceVisible = true
                }
            )
            SettingsDivider()
            SettingsRow(
                title = "从备份恢复",
                icon = Icons.Filled.FileDownload,
                subtitle = "支持加密与明文备份，会替换当前全部记录",
                titleColor = MaterialTheme.colorScheme.error,
                enabled = !busy,
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            )
            SettingsDivider()
            SettingsRow(
                title = "更换备份口令",
                icon = Icons.Filled.Key,
                subtitle = "解旧口令后用新口令重新加密，生成新文件",
                enabled = !busy,
                onClick = {
                    rekeyPickLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = "整理数据库",
                icon = Icons.Filled.Storage,
                subtitle = compactSubtitle(compactState),
                enabled = !busy && compactState !is MainViewModel.CompactState.Running,
                onClick = { viewModel.compactDatabase() }
            )
            if (busy || status != null) {
                SettingsDivider()
                Text(
                    text = status ?: "处理中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (busy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            SettingsNote(
                text = "加密备份需要口令才能恢复，口令遗失无法找回，请与备份文件分开保管。明文备份不加密，能读到文件的人就能查看全部记录。",
                isWarning = true
            )
        }

        MoreSection(
            updateViewModel = updateViewModel,
            availableVersion = availableVersion,
            privacyExpanded = privacyExpanded,
            onOpenChangelog = { changelogVisible = true },
            onOpenLicenses = { licensesVisible = true },
            onTogglePrivacy = { privacyExpanded = !privacyExpanded }
        )

        // 应用更新已并入「关于」分组，页脚只剩版本号与免责文案。
        SettingsFooter(versionName = BuildConfig.VERSION_NAME)
    }

    if (themeDialogVisible) {
        AlertDialog(
            onDismissRequest = { themeDialogVisible = false },
            title = { Text("外观") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "仅影响应用显示，不改变记录数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 复用原有三分段：大字号 52dp 与「系统」缩写适配都保留在这里。
                    ThemeModeRow(selected = themeMode, onSelect = viewModel::setThemeMode)
                    Text(
                        text = "跟随系统时随设备的深色模式自动切换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialogVisible = false }) { Text("完成") }
            }
        )
    }

    if (varietiesDialogVisible) {
        DrinkVarietyDialog(
            customCoffee = customCoffeeVarieties,
            customTea = customTeaVarieties,
            onAddCoffee = viewModel::addCustomCoffeeVariety,
            onAddTea = viewModel::addCustomTeaVariety,
            onRemoveCoffee = viewModel::removeCustomCoffeeVariety,
            onRemoveTea = viewModel::removeCustomTeaVariety,
            onDismiss = { varietiesDialogVisible = false }
        )
    }

    if (changelogVisible) {
        VersionHistoryDialog(onDismiss = { changelogVisible = false })
    }

    if (exportChoiceVisible) {
        AlertDialog(
            onDismissRequest = {
                exportChoiceVisible = false
                exportPassphrase = ""
                exportPassphraseConfirm = ""
                exportPassphraseError = null
            },
            title = { Text("导出备份") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioOptionRow(
                        selected = exportAsEncrypted,
                        title = "加密（推荐）",
                        subtitle = "需要口令才能恢复；口令遗失无法找回",
                        onClick = { exportAsEncrypted = true }
                    )
                    RadioOptionRow(
                        selected = !exportAsEncrypted,
                        title = "明文（不推荐）",
                        subtitle = "任何拿到文件的人都能查看全部记录",
                        onClick = { exportAsEncrypted = false }
                    )
                    if (exportAsEncrypted) {
                        val exportQuality = remember(exportPassphrase) {
                            PassphraseQuality.evaluate(exportPassphrase.toCharArray())
                        }
                        OutlinedTextField(
                            value = exportPassphrase,
                            onValueChange = {
                                exportPassphrase = it
                                exportPassphraseError = null
                            },
                            label = { Text("备份口令") },
                            supportingText = {
                                Text(
                                    text = passphraseHint(exportQuality, exportPassphrase.isEmpty()),
                                    color = if (exportPassphrase.isNotEmpty() &&
                                        (exportQuality.level == PassphraseQuality.Level.TOO_SHORT ||
                                            exportQuality.level == PassphraseQuality.Level.WEAK)
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = exportPassphraseConfirm,
                            onValueChange = {
                                exportPassphraseConfirm = it
                                exportPassphraseError = null
                            },
                            label = { Text("再输一次口令") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        exportPassphraseError?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (!exportAsEncrypted) {
                            exportChoiceVisible = false
                            exportPassphrase = ""
                            exportPassphraseConfirm = ""
                            plainExportWarningVisible = true
                            return@TextButton
                        }
                        val error = when {
                            exportPassphrase.length < BackupCodec.MIN_PASSPHRASE_LENGTH ->
                                "口令至少 ${BackupCodec.MIN_PASSPHRASE_LENGTH} 位。"

                            exportPassphrase != exportPassphraseConfirm -> "两次输入的口令不一致。"
                            else -> null
                        }
                        if (error != null) {
                            exportPassphraseError = error
                        } else {
                            pendingExportPassphrase = exportPassphrase.toCharArray()
                            exportChoiceVisible = false
                            exportPassphrase = ""
                            exportPassphraseConfirm = ""
                            exportLauncher.launch(backupFileName())
                        }
                    }
                ) { Text(if (exportAsEncrypted) "导出" else "下一步") }
            },
            dismissButton = {
                TextButton(onClick = {
                    exportChoiceVisible = false
                    exportPassphrase = ""
                    exportPassphraseConfirm = ""
                    exportPassphraseError = null
                }) { Text("取消") }
            }
        )
    }

    if (plainExportWarningVisible) {
        AlertDialog(
            onDismissRequest = { plainExportWarningVisible = false },
            title = { Text("确认明文导出？") },
            text = {
                Text(
                    "明文备份不加密：拿到文件的任何人（包括云盘、聊天工具、电脑上的其他应用）" +
                        "都能看到全部记录，其中会包含已启用的敏感记录（" +
                        SensitiveTypes.sensitiveModuleNames.joinToString("、") +
                        "）。加密导出只需要记一个口令，建议改用加密。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    plainExportWarningVisible = false
                    exportLauncher.launch(backupFileName())
                }) {
                    Text("仍要明文导出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    plainExportWarningVisible = false
                    exportAsEncrypted = true
                    exportChoiceVisible = true
                }) { Text("改用加密") }
            }
        )
    }

    val encryptedText = pendingEncryptedText
    if (encryptedText != null) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    backupController.cancelRestore()
                    importPassphrase = ""
                }
            },
            title = { Text("输入备份口令") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("该备份已加密，需要导出时设置的口令才能恢复。")
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = {
                            importPassphrase = it
                            backupController.clearImportError()
                        },
                        label = { Text("备份口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    importPassphraseError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && importCooldown == 0,
                    onClick = {
                        if (importPassphrase.isBlank()) {
                            backupController.setImportError("请输入口令。")
                            return@TextButton
                        }
                        backupController.submitRestorePassphrase(importPassphrase.toCharArray())
                        importPassphrase = ""
                    }
                ) {
                    Text(if (importCooldown > 0) "请等待 $importCooldown 秒" else "解密")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupController.cancelRestore()
                    importPassphrase = ""
                }) { Text("取消") }
            }
        )
    }

    val rekeyText = rekeySourceText
    if (rekeyText != null) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    backupController.cancelRekey()
                    rekeyOldPassphrase = ""
                    rekeyNewPassphrase = ""
                    rekeyNewPassphraseConfirm = ""
                }
            },
            title = { Text("更换备份口令") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("会生成一份新文件，原备份保持不变；确认新文件能恢复后，再删除旧文件。")
                    OutlinedTextField(
                        value = rekeyOldPassphrase,
                        onValueChange = {
                            rekeyOldPassphrase = it
                            backupController.clearRekeyError()
                        },
                        label = { Text("当前口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val rekeyQuality = remember(rekeyNewPassphrase) {
                        PassphraseQuality.evaluate(rekeyNewPassphrase.toCharArray())
                    }
                    OutlinedTextField(
                        value = rekeyNewPassphrase,
                        onValueChange = {
                            rekeyNewPassphrase = it
                            backupController.clearRekeyError()
                        },
                        label = { Text("新口令") },
                        supportingText = {
                            Text(
                                text = if (rekeyNewPassphrase.isEmpty()) {
                                    "至少 ${PassphraseQuality.MIN_LENGTH} 位，且不能与当前口令相同"
                                } else {
                                    passphraseHint(rekeyQuality, empty = false)
                                },
                                color = if (rekeyNewPassphrase.isNotEmpty() &&
                                    (rekeyQuality.level == PassphraseQuality.Level.TOO_SHORT ||
                                        rekeyQuality.level == PassphraseQuality.Level.WEAK)
                                ) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rekeyNewPassphraseConfirm,
                        onValueChange = {
                            rekeyNewPassphraseConfirm = it
                            backupController.clearRekeyError()
                        },
                        label = { Text("再输一次新口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    rekeyError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && rekeyCooldown == 0,
                    onClick = {
                        val error = when {
                            rekeyOldPassphrase.isBlank() -> "请输入当前口令。"
                            rekeyNewPassphrase.length < BackupCodec.MIN_PASSPHRASE_LENGTH ->
                                "新口令至少 ${BackupCodec.MIN_PASSPHRASE_LENGTH} 位。"

                            rekeyNewPassphrase == rekeyOldPassphrase -> "新口令不能与当前口令相同。"
                            rekeyNewPassphrase != rekeyNewPassphraseConfirm -> "两次输入的新口令不一致。"
                            else -> null
                        }
                        if (error != null) {
                            backupController.setRekeyError(error)
                            return@TextButton
                        }
                        backupController.submitRekey(
                            oldPassphrase = rekeyOldPassphrase.toCharArray(),
                            newPassphrase = rekeyNewPassphrase.toCharArray()
                        )
                        rekeyOldPassphrase = ""
                        rekeyNewPassphrase = ""
                        rekeyNewPassphraseConfirm = ""
                    }
                ) {
                    Text(if (rekeyCooldown > 0) "请等待 $rekeyCooldown 秒" else "生成新文件")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupController.cancelRekey()
                    rekeyOldPassphrase = ""
                    rekeyNewPassphrase = ""
                    rekeyNewPassphraseConfirm = ""
                }) { Text("取消") }
            }
        )
    }

    if (timeoutDialogVisible) {
        AlertDialog(
            onDismissRequest = { timeoutDialogVisible = false },
            title = { Text("自动锁定") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppLockTimeout.entries.forEach { option ->
                        RadioOptionRow(
                            selected = appLockTimeout == option,
                            title = option.label(),
                            subtitle = option.description(),
                            onClick = { appLockViewModel.setAppLockTimeout(option) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { timeoutDialogVisible = false }) { Text("完成") }
            }
        )
    }

    val lockMessage = appLockError
    if (lockMessage != null) {
        AlertDialog(
            onDismissRequest = { appLockError = null },
            title = { Text("无法开启应用锁") },
            text = { Text(lockMessage) },
            confirmButton = {
                TextButton(onClick = { appLockError = null }) { Text("知道了") }
            }
        )
    }

    if (licensesVisible) {
        AlertDialog(
            onDismissRequest = { licensesVisible = false },
            title = { Text("开源许可") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SQLCipher Community Edition",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = SQLCIPHER_COPYRIGHT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "许可全文如下，可上下滑动查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SQLCIPHER_LICENSE,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = SQLCIPHER_COMPONENTS,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { licensesVisible = false }) { Text("关闭") }
            }
        )
    }

    val payload = pending
    if (payload != null) {
        AlertDialog(
            onDismissRequest = { backupController.cancelRestore() },
            title = { Text("用备份替换当前数据？") },
            text = {
                Text(
                    "备份包含 $pendingCount 条记录，当前有 $currentCount 条。" +
                        "恢复后当前全部记录与详情都会被替换，无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = { backupController.confirmRestore() }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { backupController.cancelRestore() }) { Text("取消") } }
        )
    }

    val message = failure
    if (message != null) {
        AlertDialog(
            onDismissRequest = { backupController.clearFailure() },
            title = { Text("操作失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupController.clearFailure() }) { Text("知道了") } }
        )
    }
}

/** 「整理数据库」行的副标题：把状态翻成一句用户能读懂的话。 */
private fun compactSubtitle(state: MainViewModel.CompactState): String = when (state) {
    MainViewModel.CompactState.Idle -> "回收本地数据占用的多余空间"
    MainViewModel.CompactState.Running -> "正在整理，请勿离开…"
    is MainViewModel.CompactState.Done -> {
        val saved = (state.beforeBytes - state.afterBytes).coerceAtLeast(0L)
        if (saved <= 0L) {
            "已整理 · ${formatBytes(state.afterBytes)}（无可回收空间）"
        } else {
            "已整理 · ${formatBytes(state.beforeBytes)} → ${formatBytes(state.afterBytes)}，" +
                "回收 ${formatBytes(saved)}"
        }
    }

    is MainViewModel.CompactState.Failed -> "整理失败：${state.message}"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%d KB".format(bytes / 1024)
    else -> "$bytes B"
}

private fun backupFileName(suffix: String? = null): String {
    val stamp = LocalDateTime.now().format(backupStampFormatter)
    val tag = suffix?.let { "-$it" }.orEmpty()
    return "LifeTrack-backup-$stamp$tag.json"
}

private fun AppLockTimeout.label(): String = when (this) {
    AppLockTimeout.IMMEDIATE -> "立即"
    AppLockTimeout.ONE_MINUTE -> "1 分钟"
    AppLockTimeout.FIVE_MINUTES -> "5 分钟"
}

private fun AppLockTimeout.description(): String = when (this) {
    AppLockTimeout.IMMEDIATE -> "切到后台就锁定，最严格"
    AppLockTimeout.ONE_MINUTE -> "离开 1 分钟内回来不用重新解锁"
    AppLockTimeout.FIVE_MINUTES -> "适合频繁在应用之间切换"
}

/** 口令强度文案：空输入给规则提示，有输入才评强弱，避免一打开就报「偏弱」。 */
private fun passphraseHint(quality: PassphraseQuality.Result, empty: Boolean): String = when {
    empty -> "至少 ${PassphraseQuality.MIN_LENGTH} 位，建议用一句话，避免生日和手机号"
    quality.level == PassphraseQuality.Level.TOO_SHORT ->
        "至少 ${PassphraseQuality.MIN_LENGTH} 位。"

    quality.level == PassphraseQuality.Level.WEAK -> "偏弱：再长一些，或加入数字 / 符号。"
    quality.level == PassphraseQuality.Level.FAIR -> "可用：长度和字符种类都够了。"
    else -> "较强。"
}

@Composable
private fun RadioOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 主题三选一。与历史页「列表 / 月历」同构，保证全站的多选一控件长得一样。
 * 大字号下「跟随系统」四个字放不进一格，缩成「系统」，完整说明由下方的辅助文字兜住。
 */
@Composable
private fun ThemeModeRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val barHeight = if (fontScale > 1.3f) 52.dp else 40.dp
    val isLargeFont = fontScale > 1.3f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .clip(RoundedCornerShape((barHeight - 6.dp) / 2))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(mode) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLargeFont && mode == ThemeMode.SYSTEM) "系统" else mode.label,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 「关于」分区（界面定稿 v2 · 批 3 · P2）：更新日志、开源许可、数据与隐私、应用更新。
 *
 * 「应用更新」原为一张独立卡，夹在分组与页脚之间归属不清；现在并入同一张卡的最后一行，
 * 页面层级不增加。展开态与弹窗可见性仍由 [SettingsScreen] 持有。
 */
@Composable
private fun MoreSection(
    updateViewModel: UpdateViewModel,
    availableVersion: String?,
    privacyExpanded: Boolean,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onTogglePrivacy: () -> Unit
) {
    SettingsSection(title = "关于") {
        SettingsRow(
            title = "更新日志",
            icon = Icons.Filled.History,
            subtitle = if (availableVersion != null) {
                "v$availableVersion 已发布 · 向下查看更新"
            } else {
                "最近 6 个版本的变更"
            },
            onClick = { onOpenChangelog() },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (availableVersion != null) UpdateBadge()
                    ChevronTrailing()
                }
            }
        )
        SettingsDivider()
        SettingsRow(
            title = "开源许可",
            icon = Icons.Filled.Description,
            subtitle = "SQLCipher Community Edition · BSD-style",
            onClick = { onOpenLicenses() }
        )
        SettingsDivider()
        SettingsRow(
            title = "数据与隐私",
            icon = Icons.Filled.PrivacyTip,
            subtitle = "本地存储、网络权限与备份边界",
            onClick = { onTogglePrivacy() },
            trailing = {
                Icon(
                    imageVector = if (privacyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (privacyExpanded) "收起说明" else "展开说明",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        AnimatedVisibility(visible = privacyExpanded) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "记录仅保存在本应用的私有存储中，不上传任何服务器。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "为应用内「检查更新」与安装，本应用申请了网络权限（INTERNET）与安装权限" +
                        "（REQUEST_INSTALL_PACKAGES）；该权限仅用于查询 Gitee Releases 上的最新版本" +
                        "（Gitee 不可用时回退 GitHub Releases）与下载本应用安装包，" +
                        "不收集、不上传任何用户数据、设备标识或遥测。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "应用锁使用系统生物识别权限（USE_BIOMETRIC）调用指纹或锁屏密码验证，" +
                        "不读取、不上传、不保存任何生物特征数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "系统自动云备份与设备迁移已通过 Manifest 与规则文件排除应用数据；卸载或清除数据仍可能丢失记录，建议定期导出备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingsDivider()
        UpdateSection(viewModel = updateViewModel)
    }
}

/**
 * 高频项主卡：记录项目与外观。不加分区标题，第一张卡就是默认层级。
 */
@Composable
private fun PrimarySection(
    trackTypes: List<TrackTypeEntity>,
    themeMode: ThemeMode,
    onOpenModules: () -> Unit,
    onOpenVarieties: () -> Unit,
    onOpenTheme: () -> Unit
) {
    SettingsCard {
        SettingsRow(
            title = "记录项目",
            icon = Icons.Filled.Tune,
            subtitle = if (trackTypes.isEmpty()) {
                "正在载入…"
            } else {
                "已启用 ${trackTypes.count { it.enabled }} / 共 ${trackTypes.size} 项 · 启用与排序"
            },
            onClick = onOpenModules,
            trailing = { ChevronTrailing() }
        )
        SettingsDivider()
        SettingsRow(
            title = "饮品品种",
            icon = Icons.Filled.LocalCafe,
            subtitle = "管理自定义的咖啡 / 茶品种",
            onClick = onOpenVarieties,
            trailing = { ChevronTrailing() }
        )
        SettingsDivider()
        SettingsRow(
            title = "外观",
            icon = Icons.Filled.DarkMode,
            subtitle = themeMode.label,
            onClick = { onOpenTheme() },
            trailing = { ChevronTrailing() }
        )
    }
}

/**
 * 自定义饮品品种管理：咖啡与茶两组，各自可新增 / 删除自定义项。
 * 预设品种由 CoffeeFields/TeaFields 固定、不可删；这里只管用户自己加的清单。
 */
@Composable
private fun DrinkVarietyDialog(
    customCoffee: List<String>,
    customTea: List<String>,
    onAddCoffee: (String) -> Unit,
    onAddTea: (String) -> Unit,
    onRemoveCoffee: (String) -> Unit,
    onRemoveTea: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var coffeeInput by remember { mutableStateOf("") }
    var teaInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("饮品品种") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "预设品种固定，不能删除；这里管理你自己添加的品种。添加后在记录咖啡 / 茶时可直接选用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VarietySection(
                    heading = "咖啡品种",
                    presets = CoffeeFields.PRESET_VARIETIES,
                    customs = customCoffee,
                    input = coffeeInput,
                    maxLength = CoffeeFields.MAX_VARIETY_LENGTH,
                    onInput = { coffeeInput = it },
                    onAdd = {
                        val name = coffeeInput.trim()
                        if (name.isNotEmpty() && CoffeeFields.isValidVariety(name)) {
                            onAddCoffee(name)
                            coffeeInput = ""
                        }
                    },
                    onRemove = onRemoveCoffee
                )
                VarietySection(
                    heading = "茶品种",
                    presets = TeaFields.PRESET_VARIETIES,
                    customs = customTea,
                    input = teaInput,
                    maxLength = TeaFields.MAX_VARIETY_LENGTH,
                    onInput = { teaInput = it },
                    onAdd = {
                        val name = teaInput.trim()
                        if (name.isNotEmpty() && TeaFields.isValidVariety(name)) {
                            onAddTea(name)
                            teaInput = ""
                        }
                    },
                    onRemove = onRemoveTea
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun VarietySection(
    heading: String,
    presets: List<String>,
    customs: List<String>,
    input: String,
    maxLength: Int,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        Text(
            "预设：${presets.joinToString("、")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { onInput(it.take(maxLength)) },
                modifier = Modifier.weight(1f),
                label = { Text("新增品种") },
                singleLine = true
            )
            TextButton(onClick = onAdd) { Text("添加") }
        }
        if (customs.isEmpty()) {
            Text(
                "还没有自定义品种",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                customs.forEach { name ->
                    FilterChip(
                        selected = false,
                        onClick = { onRemove(name) },
                        label = { Text("$name ×") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "删除 $name",
                                modifier = Modifier.height(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
