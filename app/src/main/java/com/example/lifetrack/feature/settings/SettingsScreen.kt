package com.example.lifetrack.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Card {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("深色模式", style = MaterialTheme.typography.titleMedium)
                    Text("仅影响应用显示，不改变记录数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
        }
        ModuleSettingsCard(
            types = trackTypes,
            onToggle = viewModel::setTypeEnabled,
            onMove = viewModel::moveType
        )
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
                Text(
                    "导出为单个 JSON 文件，包含全部记录与详情。恢复会用备份内容替换当前全部记录，且无法撤销。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "备份文件不加密，能读到该文件的人就能查看全部记录，请自行妥善保管。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            exportLauncher.launch(
                                "LifeTrack-backup-${LocalDateTime.now().format(backupStampFormatter)}.json"
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("导出备份") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("从备份恢复") }
                }
                when {
                    busy -> Text("处理中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    status != null -> Text(status!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据与隐私", style = MaterialTheme.typography.titleMedium)
                Text("记录仅保存在本应用的私有存储中。此版本不申请网络权限，也不提供账号或云同步。", style = MaterialTheme.typography.bodyMedium)
                Text("系统自动云备份和设备迁移已通过 Manifest 与规则文件排除应用数据。卸载应用或清除应用数据仍可能造成记录丢失，建议定期导出备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于生活记", style = MaterialTheme.typography.titleMedium)
                Text("版本 0.1.0 · 本地生活记录工具")
                Text("本产品用于个人记录与回顾，不提供医疗诊断。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
