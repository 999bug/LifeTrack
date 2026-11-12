package com.example.lifetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifetrack.core.model.ThemeMode
import com.example.lifetrack.core.navigation.LifeTrackApp
import com.example.lifetrack.core.ui.DatabaseLoadingScreen
import com.example.lifetrack.core.ui.DatabaseUnavailableScreen
import com.example.lifetrack.core.ui.LifeTrackTheme
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.lock.AppLockGate

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
                    // 截屏 / 录屏不再做任何限制（0.6.x 起移除 FLAG_SECURE），
                    // 应用锁只负责「进正文前验证」，与窗口标志无关。
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
