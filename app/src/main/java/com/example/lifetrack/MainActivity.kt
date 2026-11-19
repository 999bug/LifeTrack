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
import com.example.lifetrack.feature.home.HistoryViewModel
import com.example.lifetrack.feature.settings.SettingsViewModel
import com.example.lifetrack.feature.home.StatisticsViewModel
import com.example.lifetrack.feature.home.TodayViewModel
import com.example.lifetrack.feature.lock.AppLockGate
import com.example.lifetrack.feature.lock.AppLockViewModel
import com.example.lifetrack.feature.menstruation.PeriodViewModel
import com.example.lifetrack.feature.settings.UpdateViewModel
import com.example.lifetrack.feature.sport.SportViewModel

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
                    val mainViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            typeCatalogRepository = container.typeCatalogRepository,
                            backupRepository = container.backupRepository,
                            maintenanceRepository = container.maintenanceRepository,
                            settingsRepository = container.settingsRepository,
                            todaySource = container.todaySource
                        )
                    )
                    val appLockViewModel: AppLockViewModel = viewModel(
                        factory = AppLockViewModel.Factory(
                            settingsRepository = container.settingsRepository,
                            todaySource = container.todaySource
                        )
                    )
                    val updateViewModel: UpdateViewModel = viewModel(
                        factory = UpdateViewModel.Factory(container.updateRepository)
                    )
                    val periodViewModel: PeriodViewModel = viewModel(
                        factory = PeriodViewModel.Factory(
                            recordRepository = container.recordRepository,
                            typeCatalogRepository = container.typeCatalogRepository,
                            settingsRepository = container.settingsRepository,
                            todaySource = container.todaySource
                        )
                    )
                    val todayViewModel: TodayViewModel = viewModel(
                        factory = TodayViewModel.Factory(
                            recordRepository = container.recordRepository,
                            typeCatalogRepository = container.typeCatalogRepository,
                            todaySource = container.todaySource
                        )
                    )
                    val historyViewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModel.Factory(
                            recordRepository = container.recordRepository,
                            statisticsRepository = container.statisticsRepository,
                            typeCatalogRepository = container.typeCatalogRepository,
                            todaySource = container.todaySource
                        )
                    )
                    val statisticsViewModel: StatisticsViewModel = viewModel(
                        factory = StatisticsViewModel.Factory(
                            statisticsRepository = container.statisticsRepository,
                            typeCatalogRepository = container.typeCatalogRepository,
                            todaySource = container.todaySource
                        )
                    )
                    val sportViewModel: SportViewModel = viewModel(
                        factory = SportViewModel.Factory(
                            recordRepository = container.recordRepository,
                            typeCatalogRepository = container.typeCatalogRepository,
                            todaySource = container.todaySource
                        )
                    )
                    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
                    val appLocked by appLockViewModel.appLocked.collectAsStateWithLifecycle()
                    // 「跟随系统」在这里解析成实际的明暗，主题切换只影响本次组合，不需要重建 Activity。
                    val darkTheme = when (themeMode) {
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    }
                    LifeTrackTheme(darkTheme = darkTheme) {
                        AppLockGate(locked = appLocked, viewModel = appLockViewModel) {
                            LifeTrackApp(
                                viewModel = mainViewModel,
                                appLockViewModel = appLockViewModel,
                                updateViewModel = updateViewModel,
                                periodViewModel = periodViewModel,
                                todayViewModel = todayViewModel,
                                historyViewModel = historyViewModel,
                                statisticsViewModel = statisticsViewModel,
                                sportViewModel = sportViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
