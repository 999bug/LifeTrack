package com.example.lifetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.core.ui.LifeTrackTheme
import com.example.lifetrack.core.navigation.LifeTrackApp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LifeTrackApplication).container
        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    repository = container.trackRepository,
                    settingsRepository = container.settingsRepository
                )
            )
            val darkTheme by mainViewModel.darkTheme.collectAsStateWithLifecycle()
            LifeTrackTheme(darkTheme = darkTheme) {
                LifeTrackApp(viewModel = mainViewModel)
            }
        }
    }
}
