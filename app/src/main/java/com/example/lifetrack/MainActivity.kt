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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LifeTrackApplication).container

        // 开启应用锁后隐藏最近任务预览与截屏；关掉时立即恢复。
        lifecycleScope.launch {
            container.settingsRepository.appLockEnabled.collect { enabled ->
                if (enabled) {
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
