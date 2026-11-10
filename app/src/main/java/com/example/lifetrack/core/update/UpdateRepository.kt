package com.example.lifetrack.core.update

import android.content.Context
import com.example.lifetrack.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内「检查更新 / 下载安装」的状态机与入口。
 * 持有 [StateFlow] 供 UI 观察；同一进程内启动自动检查只跑一次。
 * 版本查询走 [ReleaseFetcher]：Gitee 优先，不可用时回退 GitHub。
 */
class UpdateRepository(private val appContext: Context) {

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: ReleaseInfo) : Status
        data class Downloading(val progress: Float) : Status
        data object Ready : Status
        data class Error(val message: String) : Status
    }

    private val _state = MutableStateFlow<Status>(Status.Idle)
    val state: StateFlow<Status> = _state.asStateFlow()

    private var startupChecked = false

    /**
     * 检查更新。[auto] 为 true 时（启动自动检查）同一进程内只查一次。
     */
    suspend fun check(auto: Boolean = false) {
        if (auto && startupChecked) return
        startupChecked = true
        if (_state.value is Status.Checking) return
        _state.value = Status.Checking
        runCatching {
            val latest = ReleaseFetcher.fetchLatest()
            if (latest == null) {
                _state.value = Status.UpToDate
                return@runCatching
            }
            _state.value = if (GitHubReleaseChecker.isNewer(latest.version, BuildConfig.VERSION_NAME)) {
                Status.Available(latest)
            } else {
                Status.UpToDate
            }
        }.onFailure {
            _state.value = Status.Error(it.message ?: "检查更新失败")
        }
    }

    /**
     * 下载并调起安装。下载进度通过 [Status.Downloading] 回报；完成后切到 [Status.Ready]，
     * 实际安装由系统安装器承接。
     */
    suspend fun downloadAndInstall(release: ReleaseInfo) {
        val url = release.downloadUrl
        if (url.isNullOrBlank()) {
            _state.value = Status.Error("未找到可用的 APK 下载地址")
            return
        }
        _state.value = Status.Downloading(0f)
        runCatching {
            val file = ApkDownloader.download(appContext, url, "LifeTrack-${release.version}.apk") { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                _state.value = Status.Downloading(progress)
            }
            ApkInstaller.install(appContext, file)
            _state.value = Status.Ready
        }.onFailure {
            _state.value = Status.Error(it.message ?: "下载失败")
        }
    }
}
