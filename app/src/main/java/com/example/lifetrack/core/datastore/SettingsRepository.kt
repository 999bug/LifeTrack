package com.example.lifetrack.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lifetrack.core.model.AppLockTimeout
import com.example.lifetrack.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "life_track_settings")

class SettingsRepository(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")
    private val appLockTimeoutKey = intPreferencesKey("app_lock_timeout_minutes")

    val darkTheme: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[darkThemeKey] ?: false
    }

    /**
     * 主题模式。0.6.x 只存过一个 `dark_theme` 布尔值，这里按它推导出 LIGHT / DARK，
     * 老用户升级后不会突然跳到浅色；只有用户主动选过三分段才会有 `theme_mode`。
     */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        ThemeMode.fromName(preferences[themeModeKey])
            ?: if (preferences[darkThemeKey] == true) ThemeMode.DARK else ThemeMode.LIGHT
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
            // 同步旧键：回退到不认识 theme_mode 的版本时，深色偏好不至于丢失
            preferences[darkThemeKey] = mode == ThemeMode.DARK
        }
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
