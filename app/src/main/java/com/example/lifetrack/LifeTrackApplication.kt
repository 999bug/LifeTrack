package com.example.lifetrack

import android.app.Application
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.datastore.SettingsRepository
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
            container.database.trackTypeDao().insertIgnore(
                TrackTypeEntity(
                    code = "poop",
                    name = "排便",
                    iconKey = "poop",
                    category = "body",
                    trackingMode = "EVENT",
                    enabled = true,
                    sortOrder = 10,
                    schemaVersion = 1
                )
            )
        }
    }
}

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.create(application)
    val trackRepository = RoomTrackRepository(database)
    val settingsRepository = SettingsRepository(application)
}
