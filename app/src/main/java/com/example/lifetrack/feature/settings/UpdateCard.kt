package com.example.lifetrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.BuildConfig
import com.example.lifetrack.core.update.ReleaseInfo
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.feature.home.MainViewModel

@Composable
fun UpdateCard(viewModel: MainViewModel) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("检查更新", style = MaterialTheme.typography.titleMedium)
            Text(
                "当前版本 ${BuildConfig.VERSION_NAME}。仅向 GitHub Releases 查询最新版本，不上传任何数据。",
                style = MaterialTheme.typography.bodyMedium
            )
            when (val s = state) {
                is UpdateRepository.Status.Idle -> {
                    OutlinedButton(onClick = { viewModel.checkForUpdate() }) { Text("检查更新") }
                }

                is UpdateRepository.Status.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("检查中…")
                    }
                }

                is UpdateRepository.Status.UpToDate -> {
                    Text("已是最新版本。", color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { viewModel.checkForUpdate() }) { Text("检查更新") }
                }

                is UpdateRepository.Status.Available -> {
                    UpdateAvailable(
                        release = s.release,
                        onDownload = { viewModel.downloadAndInstall(s.release) }
                    )
                }

                is UpdateRepository.Status.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("下载中 ${(s.progress * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is UpdateRepository.Status.Ready -> {
                    Text(
                        "已调起系统安装器，请在弹窗中完成安装。",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is UpdateRepository.Status.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.checkForUpdate() }) { Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailable(release: ReleaseInfo, onDownload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "发现新版本 v${release.version}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        release.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }?.let { firstLine ->
            Text(
                firstLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (release.downloadUrl.isNullOrBlank()) {
            Text(
                "未找到 APK 下载地址，请前往 Releases 页面手动下载。",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Button(onClick = onDownload) { Text("下载并安装") }
        }
    }
}
