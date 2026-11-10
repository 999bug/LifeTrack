package com.example.lifetrack.feature.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.lifetrack.feature.home.MainViewModel

/**
 * 应用锁入口。
 *
 * 锁定时**不销毁正文**：设置页可能正处在 SAF 选文件 / 写文件的流程中，
 * 拆掉 composition 会取消它的协程并把导出写成半截。
 * 这里改用全屏 Dialog 盖住正文，同时清掉正文的语义树，两件事一起做：
 * 视觉上挡住、读屏也读不到；正文里的 AlertDialog 是独立窗口，普通遮罩压不住，
 * 后创建的 Dialog 窗口天然在它上面。
 */
@Composable
fun AppLockGate(
    locked: Boolean?,
    viewModel: MainViewModel,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (locked) {
        // 设置还没读出来：先给一块空白背景，避免正文闪一下再锁。
        null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                val contentModifier = if (locked) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    Modifier
                }
                Box(modifier = contentModifier) { content() }
                if (locked) {
                    LockScreenDialog(
                        onUnlocked = viewModel::unlockApp,
                        onDisableLock = {
                            viewModel.setAppLockEnabled(false)
                            viewModel.unlockApp()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LockScreenDialog(
    onUnlocked: () -> Unit,
    onDisableLock: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val authenticator = remember(activity) { activity?.let { AppLockAuthenticator(it) } }
    var authenticating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val signal = remember { CancellationSignal() }

    val credentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authenticating = false
        if (result.resultCode == Activity.RESULT_OK) onUnlocked()
    }

    fun requestUnlock() {
        if (authenticating) return
        error = null
        val act = activity
        if (act == null || authenticator == null) {
            unavailable = true
            error = "当前环境不支持应用锁验证。"
            return
        }
        if (!authenticator.isAvailable()) {
            unavailable = true
            error = "系统锁屏已关闭，无法验证。请先在系统设置中设置锁屏密码。"
            return
        }
        unavailable = false
        authenticating = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SystemBiometricPrompt.authenticate(
                activity = act,
                signal = signal,
                onSuccess = {
                    authenticating = false
                    onUnlocked()
                },
                onError = { message ->
                    authenticating = false
                    error = message
                }
            )
        } else {
            val intent = authenticator.credentialIntent()
            if (intent == null) {
                authenticating = false
                unavailable = true
                error = "系统锁屏已关闭，无法验证。"
            } else {
                credentialLauncher.launch(intent)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { signal.cancel() }
    }

    // 冷启动进入锁屏时自动拉起一次系统验证；用户取消后留在本页，可手动重试。
    LaunchedEffect(Unit) { requestUnlock() }

    // 锁屏期间拦住返回键：避免用户在系统验证弹窗上按返回时直接把 Activity 结束掉。
    BackHandler { }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "生活记",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "应用已锁定",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "解锁后才能查看记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = { requestUnlock() },
                    enabled = !authenticating
                ) {
                    Text(if (authenticating) "正在验证…" else "解锁")
                }
                error?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                if (unavailable) {
                    TextButton(onClick = onDisableLock) {
                        Text("关闭应用锁并进入")
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
