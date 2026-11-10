package com.example.lifetrack.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.BuildConfig
import com.example.lifetrack.core.backup.BackupDecodeResult
import com.example.lifetrack.core.backup.BackupError
import com.example.lifetrack.core.backup.BackupIo
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.feature.home.MainViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val backupStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

/**
 * 设置页：按「记录 / 数据 / 外观 / 关于」四段组织，每段一张卡片。
 *
 * 记录项目管理已拆到独立页面（[ModuleManagementScreen]），
 * 更新与隐私收进「关于」，备份改成两行操作 + 一句风险提示，
 * 避免之前六张卡片各写一套内边距、越加越长的问题。
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenModules: () -> Unit
) {
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val trackTypes by viewModel.trackTypes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupPayload?>(null) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var currentCount by remember { mutableIntStateOf(0) }
    var privacyExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            failure = null
            runCatching {
                val payload = viewModel.buildBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        BackupIo.writeUtf8(output, payload)
                    } ?: error("无法写入所选位置")
                }
                viewModel.countAllRecords()
            }.onSuccess { count ->
                status = "已导出 $count 条记录。"
            }.onFailure {
                failure = "导出失败：${it.message ?: "无法写入所选位置"}"
            }
            busy = false
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
                        failure = buildString {
                            append(result.error.message())
                            result.detail?.let { append("（记录 $it）") }
                        }
                    }
                }
            }
            busy = false
        }
    }

    LaunchedEffect(pending) {
        currentCount = if (pending == null) 0 else viewModel.countAllRecords()
    }

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

        SettingsSection(title = "记录") {
            SettingsRow(
                title = "记录项目",
                subtitle = if (trackTypes.isEmpty()) {
                    "正在载入…"
                } else {
                    "已启用 ${trackTypes.count { it.enabled }} / 共 ${trackTypes.size} 项 · 启用与排序"
                },
                onClick = onOpenModules,
                trailing = { ChevronTrailing() }
            )
        }

        SettingsSection(title = "数据") {
            SettingsRow(
                title = "导出备份",
                subtitle = "全部记录与详情，导出为单个 JSON 文件",
                enabled = !busy,
                onClick = {
                    exportLauncher.launch(
                        "LifeTrack-backup-${LocalDateTime.now().format(backupStampFormatter)}.json"
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = "从备份恢复",
                subtitle = "会用备份替换当前全部记录，无法撤销",
                titleColor = MaterialTheme.colorScheme.error,
                enabled = !busy,
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
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
                text = "备份文件不加密，能读到该文件的人就能查看全部记录，请自行妥善保管。",
                isWarning = true
            )
        }

        SettingsSection(title = "外观") {
            SettingsRow(
                title = "深色模式",
                subtitle = "仅影响应用显示，不改变记录数据",
                role = Role.Switch,
                onClick = { viewModel.setDarkTheme(!darkTheme) },
                trailing = {
                    Switch(checked = darkTheme, onCheckedChange = null)
                }
            )
        }

        SettingsSection(title = "关于") {
            SettingsRow(
                title = "版本",
                subtitle = "本地生活记录工具 · 不提供医疗诊断",
                trailing = {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            SettingsDivider()
            UpdateSection(viewModel = viewModel)
            SettingsDivider()
            SettingsRow(
                title = "数据与隐私",
                subtitle = "本地存储、网络权限与备份边界",
                onClick = { privacyExpanded = !privacyExpanded },
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
                        "系统自动云备份与设备迁移已通过 Manifest 与规则文件排除应用数据；卸载或清除数据仍可能丢失记录，建议定期导出备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
}
