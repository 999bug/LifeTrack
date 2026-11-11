package com.example.lifetrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.BuildConfig
import com.example.lifetrack.core.update.ReleaseInfo
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.feature.home.MainViewModel

/**
 * 「应用更新」一行：默认只占一行（标题 + 当前版本 + 检查按钮），
 * 发现新版本或下载中才在下方展开细节，避免更新卡片长期占据设置页一大块。
 */
@Composable
internal fun UpdateSection(viewModel: MainViewModel) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()

    SettingsRow(
        title = "应用更新",
            icon = Icons.Filled.SystemUpdate,
        subtitle = when (val s = state) {
            is UpdateRepository.Status.Idle -> "当前版本 ${BuildConfig.VERSION_NAME}"
            is UpdateRepository.Status.Checking -> "当前版本 ${BuildConfig.VERSION_NAME} · 检查中…"
            is UpdateRepository.Status.UpToDate -> "当前版本 ${BuildConfig.VERSION_NAME} · 已是最新"
            is UpdateRepository.Status.Available -> "发现新版本 v${s.release.version}"
            is UpdateRepository.Status.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%"
            is UpdateRepository.Status.Ready -> "已调起系统安装器"
            is UpdateRepository.Status.Error -> "检查失败：${s.message}"
        },
        trailing = {
            when (state) {
                is UpdateRepository.Status.Checking,
                is UpdateRepository.Status.Downloading -> Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }

                is UpdateRepository.Status.Available -> Unit

                is UpdateRepository.Status.Error -> TextButton(onClick = { viewModel.checkForUpdate() }) {
                    Text("重试")
                }

                else -> TextButton(onClick = { viewModel.checkForUpdate() }) { Text("检查") }
            }
        }
    )

    when (val s = state) {
        is UpdateRepository.Status.Available -> {
            SettingsDivider()
            UpdateAvailable(
                release = s.release,
                onDownload = { viewModel.downloadAndInstall(s.release) }
            )
        }

        is UpdateRepository.Status.Downloading -> {
            SettingsDivider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "下载完成后会调起系统安装器；安装前请勿清理后台。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is UpdateRepository.Status.Ready -> {
            SettingsDivider()
            Text(
                "已调起系统安装器，请在弹窗中完成安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        is UpdateRepository.Status.Error -> {
            SettingsDivider()
            Text(
                "优先向 Gitee 查询版本号与下载本应用 APK，不可用时回退 GitHub Releases；不携带任何用户数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        else -> Unit
    }
}

@Composable
private fun UpdateAvailable(release: ReleaseInfo, onDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "生活记 v${release.version} 可用",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        release.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }?.let { firstLine ->
            Text(
                text = firstLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (release.downloadUrl.isNullOrBlank()) {
            Text(
                text = "未找到 APK 下载地址，请前往 Releases 页面手动下载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDownload) { Text("下载并安装") }
            }
        }
    }
}
