package com.example.lifetrack.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.lifetrack.core.backup.BackupError
import com.example.lifetrack.core.backup.BackupIo
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.backup.BackupReencryptResult
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.AppLockTimeout
import com.example.lifetrack.core.model.ThemeMode
import com.example.lifetrack.core.security.BackupCrypto
import com.example.lifetrack.core.security.PassphraseQuality
import com.example.lifetrack.core.security.RetryBackoff
import com.example.lifetrack.core.security.ScreenCapturePolicy
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.lock.canUseAppLock
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
 * - 更多：只读或一次性入口（更新日志、开源许可、数据与隐私）。
 * - 页脚：版本号、免责文案与应用更新，沉到页面最后一段。
 *
 * 记录项目管理在独立页（[ModuleManagementScreen]）；更新状态机仍由
 * [UpdateSection] 自己维护，这里只决定它挂在哪一段。
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenModules: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val trackTypes by viewModel.trackTypes.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appLockTimeout by viewModel.appLockTimeout.collectAsStateWithLifecycle()
    // 与 MainActivity 共用同一判定口径：窗口已遮挡时，界面必须说得出原因和恢复方法。
    val captureReasons = ScreenCapturePolicy.reasons(appLockEnabled == true, trackTypes)
    val captureBlocked = captureReasons.isNotEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupPayload?>(null) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var currentCount by remember { mutableIntStateOf(0) }
    var privacyExpanded by remember { mutableStateOf(false) }
    // 数据与备份默认收起；低频项不持久化展开态，下次进来仍是一行。
    var dataExpanded by remember { mutableStateOf(false) }
    var themeDialogVisible by remember { mutableStateOf(false) }
    var timeoutDialogVisible by remember { mutableStateOf(false) }
    var appLockError by remember { mutableStateOf<String?>(null) }
    var licensesVisible by remember { mutableStateOf(false) }
    var changelogVisible by remember { mutableStateOf(false) }
    // null = 不显示；非空 = 展示当时的遮挡原因（点击瞬间 DataStore 还没回写，需现场构造）
    var captureNoticeReasons by remember { mutableStateOf<List<ScreenCapturePolicy.Reason>?>(null) }

    // 导出：先选加密方式；加密要两次口令，明文要过一道风险确认。
    var exportChoiceVisible by remember { mutableStateOf(false) }
    var exportAsEncrypted by remember { mutableStateOf(true) }
    var exportPassphrase by remember { mutableStateOf("") }
    var exportPassphraseConfirm by remember { mutableStateOf("") }
    var exportPassphraseError by remember { mutableStateOf<String?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var plainExportWarningVisible by remember { mutableStateOf(false) }

    // 恢复：加密备份先读文本，拿到口令后再解一次。
    var pendingEncryptedText by remember { mutableStateOf<String?>(null) }
    var importPassphrase by remember { mutableStateOf("") }
    var importPassphraseError by remember { mutableStateOf<String?>(null) }

    // 更换备份口令：选一份加密备份，解旧 → 用新口令重加密 → 另存为新文件。
    var rekeySourceText by remember { mutableStateOf<String?>(null) }
    var rekeyOldPassphrase by remember { mutableStateOf("") }
    var rekeyNewPassphrase by remember { mutableStateOf("") }
    var rekeyNewPassphraseConfirm by remember { mutableStateOf("") }
    var rekeyError by remember { mutableStateOf<String?>(null) }
    var pendingRekeyText by remember { mutableStateOf<String?>(null) }

    // 失败节流：累计失败次数 → 下一次尝试前的等待秒数；成功后清零。
    var importFailures by remember { mutableIntStateOf(0) }
    var importCooldown by remember { mutableIntStateOf(0) }
    var rekeyFailures by remember { mutableIntStateOf(0) }
    var rekeyCooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(importCooldown) {
        if (importCooldown > 0) {
            delay(1_000)
            importCooldown -= 1
        }
    }
    LaunchedEffect(rekeyCooldown) {
        if (rekeyCooldown > 0) {
            delay(1_000)
            rekeyCooldown -= 1
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
        scope.launch {
            busy = true
            status = null
            failure = null
            try {
                val payload = viewModel.buildBackupJson(passphrase)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        BackupIo.writeUtf8(output, payload)
                    } ?: error("无法写入所选位置")
                }
                val count = viewModel.countAllRecords()
                status = if (passphrase == null) {
                    "已导出 $count 条明文记录。"
                } else {
                    "已导出 $count 条加密记录，请牢记口令。"
                }
            } catch (e: BackupCrypto.Failure) {
                failure = "导出失败：${e.reason.exportMessage()}"
            } catch (e: Exception) {
                failure = "导出失败：${e.message ?: "无法写入所选位置"}"
            } finally {
                passphrase?.fill('\u0000')
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            failure = null
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BackupIo.readUtf8(it) }
                }.getOrNull()
            }
            if (text == null) {
                failure = "无法读取文件，或文件超过 ${BackupIo.MAX_BYTES / 1024 / 1024} MB。"
            } else {
                when (val result = viewModel.decodeBackupJson(text)) {
                    is BackupDecodeResult.Success -> {
                        pending = result.payload
                        pendingCount = result.payload.events.size
                    }

                    is BackupDecodeResult.Invalid -> {
                        failure = result.describe()
                    }

                    BackupDecodeResult.PassphraseRequired -> {
                        importPassphrase = ""
                        importPassphraseError = null
                        importFailures = 0
                        importCooldown = 0
                        pendingEncryptedText = text
                    }
                }
            }
            busy = false
        }
    }

    val rekeyPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            failure = null
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BackupIo.readUtf8(it) }
                }.getOrNull()
            }
            if (text == null) {
                failure = "无法读取文件，或文件超过 ${BackupIo.MAX_BYTES / 1024 / 1024} MB。"
            } else {
                // 不带口令先判类型：明文没有口令可换，坏文件直接给原因。
                when (val result = viewModel.decodeBackupJson(text)) {
                    is BackupDecodeResult.Success ->
                        failure = "这是未加密的备份，没有口令可以更换。"

                    is BackupDecodeResult.Invalid -> failure = result.describe()

                    BackupDecodeResult.PassphraseRequired -> {
                        rekeySourceText = text
                        rekeyOldPassphrase = ""
                        rekeyNewPassphrase = ""
                        rekeyNewPassphraseConfirm = ""
                        rekeyError = null
                        rekeyFailures = 0
                        rekeyCooldown = 0
                    }
                }
            }
            busy = false
        }
    }

    val rekeySaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = pendingRekeyText
        pendingRekeyText = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            failure = null
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        BackupIo.writeUtf8(output, text)
                    } ?: error("无法写入所选位置")
                }
                status = "已生成新口令的备份。确认能恢复后，再删除旧文件。"
            } catch (e: Exception) {
                failure = "写入失败：${e.message ?: "无法写入所选位置"}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(pending) {
        currentCount = if (pending == null) 0 else viewModel.countAllRecords()
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
                        viewModel.setAppLockEnabled(enable)
                        if (enable) {
                            // 点击瞬间 DataStore 还没回写，显式按「开启后」的状态构造原因
                            captureNoticeReasons = ScreenCapturePolicy.reasons(true, trackTypes)
                        }
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
            SettingsDivider()
            SettingsRow(
                title = "截屏与录屏",
                icon = Icons.Filled.PrivacyTip,
                subtitle = if (captureBlocked) {
                    "已禁止 · 因${ScreenCapturePolicy.describe(captureReasons)}；本应用所有页面都无法截屏、录屏，最近任务预览也不显示内容"
                } else {
                    "正常 · 可截屏、录屏，最近任务预览显示页面内容"
                },
                onClick = { captureNoticeReasons = captureReasons },
                trailing = {
                    Text(
                        text = if (captureBlocked) "已禁止" else "正常",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (captureBlocked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            )
            SettingsNote(
                text = if (captureBlocked) {
                    "点上面一行可查看完整说明与恢复方法。解锁使用系统指纹或锁屏密码，应用不保存任何生物识别信息。"
                } else {
                    "开启应用锁或启用「亲密关系」记录项目时，整个应用会自动禁止截屏与录屏，届时会再次提示。解锁使用系统指纹或锁屏密码，应用不保存任何生物识别信息。"
                }
            )
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
            availableVersion = availableVersion,
            privacyExpanded = privacyExpanded,
            onOpenChangelog = { changelogVisible = true },
            onOpenLicenses = { licensesVisible = true },
            onTogglePrivacy = { privacyExpanded = !privacyExpanded }
        )

        // 应用更新是功能入口，紧跟「更多」分区（其最后一行是「数据与隐私」），
        // 不能夹在页脚小字里；版本号与免责文案才是整页最底部。
        SettingsCard {
            UpdateSection(viewModel = viewModel)
        }
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
                        "都能看到全部记录，其中会包含已启用的亲密关系记录。" +
                        "加密导出只需要记一个口令，建议改用加密。"
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
                    pendingEncryptedText = null
                    importPassphrase = ""
                    importPassphraseError = null
                    importFailures = 0
                    importCooldown = 0
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
                            importPassphraseError = null
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
                            importPassphraseError = "请输入口令。"
                            return@TextButton
                        }
                        scope.launch {
                            busy = true
                            val passphrase = importPassphrase.toCharArray()
                            try {
                                when (val result = viewModel.decodeBackupJson(encryptedText, passphrase)) {
                                    is BackupDecodeResult.Success -> {
                                        pending = result.payload
                                        pendingCount = result.payload.events.size
                                        pendingEncryptedText = null
                                        importPassphrase = ""
                                        importPassphraseError = null
                                        importFailures = 0
                                        importCooldown = 0
                                    }

                                    is BackupDecodeResult.Invalid -> {
                                        importPassphraseError = result.describe()
                                        if (result.error == BackupError.WRONG_PASSPHRASE) {
                                            importFailures += 1
                                            importCooldown =
                                                RetryBackoff.secondsAfter(importFailures)
                                        }
                                    }

                                    BackupDecodeResult.PassphraseRequired -> {
                                        importPassphraseError = "请输入口令。"
                                    }
                                }
                            } catch (e: Exception) {
                                importPassphraseError = "解密失败：${e.message ?: "无法读取备份"}"
                            } finally {
                                passphrase.fill('\u0000')
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(if (importCooldown > 0) "请等待 $importCooldown 秒" else "解密")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingEncryptedText = null
                    importPassphrase = ""
                    importPassphraseError = null
                    importFailures = 0
                    importCooldown = 0
                }) { Text("取消") }
            }
        )
    }

    val rekeyText = rekeySourceText
    if (rekeyText != null) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    rekeySourceText = null
                    rekeyOldPassphrase = ""
                    rekeyNewPassphrase = ""
                    rekeyNewPassphraseConfirm = ""
                    rekeyError = null
                    rekeyFailures = 0
                    rekeyCooldown = 0
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
                            rekeyError = null
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
                            rekeyError = null
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
                            rekeyError = null
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
                            rekeyError = error
                            return@TextButton
                        }
                        scope.launch {
                            busy = true
                            val oldChars = rekeyOldPassphrase.toCharArray()
                            val newChars = rekeyNewPassphrase.toCharArray()
                            try {
                                when (val result = viewModel.reencryptBackup(rekeyText, oldChars, newChars)) {
                                    is BackupReencryptResult.Success -> {
                                        rekeySourceText = null
                                        rekeyOldPassphrase = ""
                                        rekeyNewPassphrase = ""
                                        rekeyNewPassphraseConfirm = ""
                                        rekeyError = null
                                        rekeyFailures = 0
                                        rekeyCooldown = 0
                                        pendingRekeyText = result.text
                                        rekeySaveLauncher.launch(backupFileName(suffix = "rekeyed"))
                                    }

                                    is BackupReencryptResult.Invalid -> {
                                        rekeyError = result.describe()
                                        if (result.error == BackupError.WRONG_PASSPHRASE) {
                                            rekeyFailures += 1
                                            rekeyCooldown = RetryBackoff.secondsAfter(rekeyFailures)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                rekeyError = "更换失败：${e.message ?: "加密异常"}"
                            } finally {
                                oldChars.fill('\u0000')
                                newChars.fill('\u0000')
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(if (rekeyCooldown > 0) "请等待 $rekeyCooldown 秒" else "生成新文件")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    rekeySourceText = null
                    rekeyOldPassphrase = ""
                    rekeyNewPassphrase = ""
                    rekeyNewPassphraseConfirm = ""
                    rekeyError = null
                    rekeyFailures = 0
                    rekeyCooldown = 0
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
                            onClick = { viewModel.setAppLockTimeout(option) }
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

    captureNoticeReasons?.let { reasons ->
        ScreenCaptureNoticeDialog(
            reasons = reasons,
            onOpenModules = onOpenModules,
            onDismiss = { captureNoticeReasons = null }
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
            onDismissRequest = { pending = null },
            title = { Text("用备份替换当前数据？") },
            text = {
                Text(
                    "备份包含 $pendingCount 条记录，当前有 $currentCount 条。" +
                        "恢复后当前全部记录与详情都会被替换，无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        busy = true
                        status = null
                        failure = null
                        runCatching { viewModel.restoreBackup(payload) }
                            .onSuccess { status = "已恢复 $pendingCount 条记录。" }
                            .onFailure { failure = "恢复失败：${it.message ?: "数据库写入异常"}" }
                        busy = false
                    }
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }
        )
    }

    val message = failure
    if (message != null) {
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text("操作失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text("知道了") } }
        )
    }
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

private fun describeError(error: BackupError, detail: String?): String = buildString {
    append(error.message())
    // 只有「定位到某条记录」的细节才对用户有意义；算法名、payload 之类的技术细节留在日志里。
    val technical = error == BackupError.INVALID_ENVELOPE ||
        error == BackupError.UNSUPPORTED_ENCRYPTION ||
        error == BackupError.UNSUPPORTED_VERSION
    if (!technical) detail?.let { append("（记录 $it）") }
}

private fun BackupDecodeResult.Invalid.describe(): String = describeError(error, detail)

private fun BackupReencryptResult.Invalid.describe(): String = describeError(error, detail)

private fun BackupCrypto.Reason.exportMessage(): String = when (this) {
    BackupCrypto.Reason.EMPTY_PASSPHRASE -> "口令不能为空。"
    BackupCrypto.Reason.PLAINTEXT_TOO_LARGE ->
        "记录过多，加密后的文件超过 ${BackupIo.MAX_BYTES / 1024 / 1024} MB 上限。"

    BackupCrypto.Reason.UNAVAILABLE -> "当前设备不支持所需的加密算法。"
    BackupCrypto.Reason.INVALID_ITERATIONS,
    BackupCrypto.Reason.INVALID_SALT,
    BackupCrypto.Reason.INVALID_IV,
    BackupCrypto.Reason.CIPHERTEXT_TOO_SHORT,
    BackupCrypto.Reason.DECRYPT_FAILED -> "加密失败，请重试。"
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
 * 「更多」分区的展示部分：更新日志、开源许可、数据与隐私。
 *
 * 只负责渲染与回调；展开态与弹窗可见性仍由 [SettingsScreen] 持有——它们和备份流程
 * 共用同一个重组作用域，先不下沉，避免把状态提升方向改掉。
 */
@Composable
private fun MoreSection(
    availableVersion: String?,
    privacyExpanded: Boolean,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onTogglePrivacy: () -> Unit
) {
    SettingsSection(title = "更多") {
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
            title = "外观",
            icon = Icons.Filled.DarkMode,
            subtitle = themeMode.label,
            onClick = { onOpenTheme() },
            trailing = { ChevronTrailing() }
        )
    }
}
