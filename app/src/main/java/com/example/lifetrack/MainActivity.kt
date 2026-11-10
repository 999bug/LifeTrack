package com.example.lifetrack

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.core.ui.LifeTrackTheme
import com.example.lifetrack.core.ui.DatabaseUnavailableScreen
import com.example.lifetrack.core.navigation.LifeTrackApp
import com.example.lifetrack.feature.lock.AppLockGate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.lifetrack.core.model.IntimacyFields
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LifeTrackApplication).container

        // 应用锁开启，或高敏感模块（A10 亲密关系）已启用时，隐藏最近任务预览与截屏。
        // 不强制改应用锁开关：其余用户与模块的行为保持 0.6.0 原样。
        lifecycleScope.launch {
            val sensitiveModuleEnabled = container.trackRepository.observeTrackTypes()
                .map { types -> types.any { it.code == IntimacyFields.TYPE_CODE && it.enabled } }
                .onStart { emit(false) }
                .catch { emit(false) }
            combine(
                container.settingsRepository.appLockEnabled,
                sensitiveModuleEnabled
            ) { lockEnabled, sensitive -> lockEnabled || sensitive }
                .collect { secure ->
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent {
            when (val state = container.databaseState) {
                is AppContainer.DatabaseState.Unavailable ->
                    DatabaseUnavailableScreen(state.failure)

                is AppContainer.DatabaseState.Ready -> {
                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModel.Factory(
                            repository = container.trackRepository,
                            settingsRepository = container.settingsRepository,
                            updateRepository = container.updateRepository
                        )
                    )
                    val darkTheme by mainViewModel.darkTheme.collectAsStateWithLifecycle()
                    val appLocked by mainViewModel.appLocked.collectAsStateWithLifecycle()
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
