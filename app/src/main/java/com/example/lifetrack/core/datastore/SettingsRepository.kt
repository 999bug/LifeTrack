package com.example.lifetrack.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lifetrack.core.model.AppLockTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "life_track_settings")

class SettingsRepository(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")
    private val appLockTimeoutKey = intPreferencesKey("app_lock_timeout_minutes")

    val darkTheme: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[darkThemeKey] ?: false
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[darkThemeKey] = enabled
        }
    }

    /** 默认关闭（D4），不打断 0.5.0 存量用户的启动流程。 */
    val appLockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[appLockEnabledKey] ?: false
    }

    val appLockTimeout: Flow<AppLockTimeout> = context.settingsDataStore.data.map { preferences ->
        AppLockTimeout.fromMinutes(preferences[appLockTimeoutKey] ?: 0)
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[appLockEnabledKey] = enabled
        }
    }

    suspend fun setAppLockTimeout(timeout: AppLockTimeout) {
        context.settingsDataStore.edit { preferences ->
            preferences[appLockTimeoutKey] = timeout.minutes
        }
    }
}
