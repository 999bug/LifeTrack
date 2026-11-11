package com.example.lifetrack.feature.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.BuildConfig
import com.example.lifetrack.core.update.PendingReason
import com.example.lifetrack.core.update.ReleaseInfo
import com.example.lifetrack.core.update.ReleaseOrigin
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.feature.home.MainViewModel

private const val GITEE_RELEASES = "https://gitee.com/saul999/LifeTrack/releases"
private const val GITHUB_RELEASES = "https://github.com/999bug/LifeTrack/releases"

/**
 * 「应用更新」一行：默认只占一行（标题 + 当前版本 + 检查按钮），
 * 有状态变化才在下方展开细节。版本已发布但安装包没就绪时只提示「同步中」，
 * 不渲染下载按钮也不显示进度条。
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
            is UpdateRepository.Status.Preparing -> "正在确认安装包…"
            is UpdateRepository.Status.UpToDate -> "当前版本 ${BuildConfig.VERSION_NAME} · 已是最新"
            is UpdateRepository.Status.Pending -> "v${s.release.version} 已发布 · 安装包同步中"
            is UpdateRepository.Status.Available -> "发现新版本 v${s.release.version} · 来自 Gitee"
            is UpdateRepository.Status.Downloading ->
                s.progress?.let { "正在下载 ${(it * 100).toInt()}%" } ?: "正在下载…"
            is UpdateRepository.Status.Verifying -> "正在校验安装包…"
            is UpdateRepository.Status.Ready -> "已调起系统安装器"
            is UpdateRepository.Status.Error -> "检查失败：${s.message}"
        },
        trailing = {
            when (state) {
                is UpdateRepository.Status.Checking,
                is UpdateRepository.Status.Preparing,
                is UpdateRepository.Status.Downloading,
                is UpdateRepository.Status.Verifying -> Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }

                is UpdateRepository.Status.Available -> Unit

                is UpdateRepository.Status.Pending,
                is UpdateRepository.Status.Error -> TextButton(onClick = { viewModel.checkForUpdate() }) {
                    Text("重试")
                }

                else -> TextButton(onClick = { viewModel.checkForUpdate() }) { Text("检查") }
            }
        }
    )

    when (val s = state) {
        is UpdateRepository.Status.Pending -> {
            SettingsDivider()
            UpdatePending(
                release = s.release,
                reason = s.reason,
                retryAfterMillis = s.retryAfterMillis,
                onRetry = { viewModel.checkForUpdate() }
            )
        }

        is UpdateRepository.Status.Available -> {
            SettingsDivider()
            UpdateAvailable(
                release = s.release,
                sizeBytes = s.sizeBytes,
                onDownload = { viewModel.downloadAndInstall(s.release) }
            )
        }

        is UpdateRepository.Status.Downloading -> {
            SettingsDivider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (s.progress != null) {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "下载完成后会调起系统安装器；安装前请勿清理后台。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is UpdateRepository.Status.Verifying -> {
            SettingsDivider()
            Text(
                "正在核对安装包完整性，通过后调起系统安装器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
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
            UpdateError(message = s.message, origin = s.origin, onRetry = { viewModel.checkForUpdate() })
        }

        else -> Unit
    }
}

@Composable
private fun OpenReleasesButton(origin: ReleaseOrigin?) {
    val context = LocalContext.current
    val url = if (origin == ReleaseOrigin.GITHUB) GITHUB_RELEASES else GITEE_RELEASES
    TextButton(onClick = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }) { Text("查看发布页") }
}

@Composable
private fun UpdatePending(
    release: ReleaseInfo,
    reason: PendingReason,
    retryAfterMillis: Long,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "生活记 v${release.version} 已发布",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = when (reason) {
                PendingReason.ARTIFACT_SYNCING -> "安装包正在同步到国内镜像，同步完成后才能下载。"
                PendingReason.PROBE_NOT_READY -> "安装包暂时无法访问，稍后会自动重试。"
                PendingReason.FALLBACK_ONLY -> "国内分发源暂不可用，请稍后重试，或前往发布页手动下载。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "约 ${retryAfterMillis / 1000} 秒后自动重试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onRetry) { Text("立即重试") }
            OpenReleasesButton(release.origin)
        }
    }
}

@Composable
private fun UpdateAvailable(release: ReleaseInfo, sizeBytes: Long, onDownload: () -> Unit) {
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
        Text(
            text = "来自 Gitee 国内镜像 · %.1f MB".format(sizeBytes / 1_048_576.0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDownload) { Text("下载并安装") }
            OpenReleasesButton(ReleaseOrigin.GITEE)
        }
    }
}

@Composable
private fun UpdateError(message: String, origin: ReleaseOrigin?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = "优先向 Gitee 查询版本与下载本应用 APK；仅查询 HTTPS GET 与下载 APK，不携带任何用户数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onRetry) { Text("重试") }
            OpenReleasesButton(origin)
        }
    }
}
