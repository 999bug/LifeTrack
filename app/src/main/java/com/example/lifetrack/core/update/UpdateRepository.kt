package com.example.lifetrack.core.update

import android.content.Context
import com.example.lifetrack.BuildConfig
import kotlin.jvm.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用内「检查更新 / 下载安装」的状态机与入口。
 *
 * 设计要点：只有 Gitee 上的 APK + sha256 两份附件齐备、且 HEAD 预检通过，
 * 才会进入 [Status.Available]（此时 UI 才渲染下载按钮）。版本已发布但制品没就绪
 * 进入 [Status.Pending] 并退避自动重查，不回退到境外源下载。
 */
class UpdateRepository(
    private val appContext: Context,
    private val fetcher: ReleaseFetcher = ReleaseFetcher,
    private val probe: ArtifactProbe = HttpArtifactProbe,
    private val retryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Pending(
            val release: ReleaseInfo,
            val reason: PendingReason,
            val retryAfterMillis: Long
        ) : Status

        data class Available(
            val release: ReleaseInfo,
            val sizeBytes: Long
        ) : Status

        /** 点击下载后、进度条出现前的预检阶段。 */
        data object Preparing : Status

        /** progress 为 null 时表示分母未知，UI 用不确定进度。 */
        data class Downloading(val progress: Float?) : Status
        data object Verifying : Status
        data object Ready : Status
        data class Error(val message: String, val origin: ReleaseOrigin?) : Status
    }

    private val _state = MutableStateFlow<Status>(Status.Idle)
    val state: StateFlow<Status> = _state.asStateFlow()

    private var startupChecked = false
    private var pendingAttempt = 0

    @Volatile
    private var retryJob: Job? = null

    /** [auto] 为 true 时（启动自动检查）同一进程内只查一次。 */
    suspend fun check(auto: Boolean = false) {
        if (auto && startupChecked) return
        startupChecked = true
        if (_state.value is Status.Checking) return
        cancelRetry()
        _state.value = Status.Checking
        runCatching {
            val latest = fetcher.fetchLatest()
            when (val decision = UpdatePolicy.decide(latest, BuildConfig.VERSION_NAME)) {
                UpdateDecision.UpToDate -> {
                    pendingAttempt = 0
                    _state.value = Status.UpToDate
                }

                is UpdateDecision.Pending -> enterPending(decision)

                is UpdateDecision.Available -> {
                    pendingAttempt = 0
                    when (val result = probe.probe(decision.artifact)) {
                        is ProbeResult.Ready -> _state.value =
                            Status.Available(decision.release, result.sizeBytes)
                        is ProbeResult.NotReady -> enterPending(
                            UpdateDecision.Pending(decision.release, PendingReason.PROBE_NOT_READY)
                        )
                        is ProbeResult.Invalid ->
                            _state.value = Status.Error(result.reason, decision.release.origin)
                    }
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            _state.value = Status.Error(e.message ?: "检查更新失败", null)
        }
    }

    /** 下载并调起安装；点击时重新预检一次，避免「检查时包在、点的时候包没了」。 */
    suspend fun downloadAndInstall(release: ReleaseInfo) {
        val artifact = release.artifact
        if (artifact == null) {
            _state.value = Status.Error("安装包尚未就绪，请稍后重试", release.origin)
            return
        }
        if (release.origin != ReleaseOrigin.GITEE) {
            _state.value = Status.Error("国内分发源不可用，请前往发布页手动下载", release.origin)
            return
        }
        cancelRetry()
        _state.value = Status.Preparing

        val result = runCatching { probe.probe(artifact) }.getOrElse { e ->
            if (e is CancellationException) throw e
            _state.value = Status.Error(e.message ?: "安装包预检失败", release.origin)
            return
        }
        if (result !is ProbeResult.Ready) {
            when (result) {
                is ProbeResult.NotReady -> enterPending(
                    UpdateDecision.Pending(release, PendingReason.PROBE_NOT_READY)
                )
                is ProbeResult.Invalid -> _state.value = Status.Error(result.reason, release.origin)
                is ProbeResult.Ready -> Unit
            }
            return
        }

        // 校验文件读不到说明同步还没真正完成，回到等待而不是硬报错。
        val expectedSha256 = runCatching {
            ApkFileValidator.parseSha256(probe.fetchChecksum(artifact.checksumUrl))
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            enterPending(UpdateDecision.Pending(release, PendingReason.PROBE_NOT_READY))
            return
        }

        runCatching {
            val file = ApkDownloader.download(
                context = appContext,
                url = artifact.downloadUrl,
                fileName = "LifeTrack-${release.version}.apk",
                expectedSize = result.sizeBytes,
                onStart = { _state.value = Status.Downloading(0f) },
                onProgress = { downloaded, total ->
                    _state.value = Status.Downloading((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            )
            _state.value = Status.Verifying
            ApkFileValidator.validate(file, expectedSha256)
            ApkInstaller.install(appContext, file)
            _state.value = Status.Ready
        }.onFailure { e ->
            if (e is CancellationException) throw e
            _state.value = Status.Error(e.message ?: "下载失败", release.origin)
        }
    }

    private fun enterPending(decision: UpdateDecision.Pending) {
        val waitMillis = PendingBackoff.delayMillis(pendingAttempt)
        pendingAttempt++
        _state.value = Status.Pending(decision.release, decision.reason, waitMillis)
        scheduleRetry(waitMillis)
    }

    private fun scheduleRetry(delayMillis: Long) {
        cancelRetry()
        // delay 远大于 launch 返回耗时，重试开始执行时先把 retryJob 摘空，
        // 这样 check() 开头的 cancelRetry() 不会把自己这个协程取消掉。
        val job = retryScope.launch {
            delay(delayMillis)
            retryJob = null
            check(auto = false)
        }
        retryJob = job
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }
}
