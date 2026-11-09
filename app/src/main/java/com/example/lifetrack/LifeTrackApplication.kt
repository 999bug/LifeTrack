package com.example.lifetrack

import android.app.Application
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.data.repository.RoomTrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifeTrackApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.database.trackTypeDao().insertIgnore(DEFAULT_POOP_TYPE)
        }
    }
}

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.create(application)
    val trackRepository = RoomTrackRepository(database)
    val settingsRepository = SettingsRepository(application)
    val updateRepository = UpdateRepository(application)
}
