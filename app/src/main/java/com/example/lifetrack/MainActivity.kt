package com.example.lifetrack

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifetrack.core.model.ThemeMode
import com.example.lifetrack.core.navigation.LifeTrackApp
import com.example.lifetrack.core.security.ScreenCapturePolicy
import com.example.lifetrack.core.ui.DatabaseLoadingScreen
import com.example.lifetrack.core.ui.DatabaseUnavailableScreen
import com.example.lifetrack.core.ui.LifeTrackTheme
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.lock.AppLockGate
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LifeTrackApplication).container

        setContent {
            val databaseState by container.databaseState.collectAsStateWithLifecycle()
            when (val state = databaseState) {
                AppContainer.DatabaseState.Loading -> DatabaseLoadingScreen()

                is AppContainer.DatabaseState.Unavailable ->
                    DatabaseUnavailableScreen(state.failure)

                is AppContainer.DatabaseState.Ready -> {
                    // 应用锁开启，或高敏感模块（A10 亲密关系）已启用时，隐藏最近任务预览与截屏。
                    // 判定口径与设置页共用 ScreenCapturePolicy，避免「窗口已遮挡、界面却说没遮挡」。
                    // 类型表要等数据库就绪才能读，所以这段随 Ready 一起进入组合。
                    LaunchedEffect(Unit) {
                        val window = this@MainActivity.window
                        val trackTypes = container.trackRepository.observeTrackTypes()
                            .onStart { emit(emptyList()) }
                            .catch { emit(emptyList()) }
                        combine(
                            container.settingsRepository.appLockEnabled,
                            trackTypes
                        ) { lockEnabled, types -> ScreenCapturePolicy.isBlocked(lockEnabled, types) }
                            .collect { secure ->
                                if (secure) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                } else {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                }
                            }
                    }

                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModel.Factory(
                            repository = container.trackRepository,
                            settingsRepository = container.settingsRepository,
                            updateRepository = container.updateRepository
                        )
                    )
                    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
                    val appLocked by mainViewModel.appLocked.collectAsStateWithLifecycle()
                    // 「跟随系统」在这里解析成实际的明暗，主题切换只影响本次组合，不需要重建 Activity。
                    val darkTheme = when (themeMode) {
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    }
                    LifeTrackTheme(darkTheme = darkTheme) {
                        AppLockGate(locked = appLocked, viewModel = mainViewModel) {
                            LifeTrackApp(viewModel = mainViewModel)
                        }
                    }
                }
            }
        }
    }
}
