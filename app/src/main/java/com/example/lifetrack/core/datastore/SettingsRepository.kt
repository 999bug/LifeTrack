package com.example.lifetrack.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lifetrack.core.model.AppLockTimeout
import com.example.lifetrack.core.model.CycleCalculator
import com.example.lifetrack.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "life_track_settings")

class SettingsRepository(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")
    private val appLockTimeoutKey = intPreferencesKey("app_lock_timeout_minutes")
    private val lastUpdateCheckAtKey = longPreferencesKey("last_update_check_at")
    private val lastKnownLatestVersionKey = stringPreferencesKey("last_known_latest_version")
    private val cycleLengthDaysKey = intPreferencesKey("cycle_length_days")
    private val periodLengthDaysKey = intPreferencesKey("period_length_days")
    private val ongoingPeriodStartKey = stringPreferencesKey("ongoing_period_start")

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

    /**
     * 周期长度（P9）：默认 28，范围与 [CycleCalculator.CYCLE_RANGE] 同源。
     * 只作为样本不足时的回退值 / 用户覆盖，预测结果本身不落库。
     */
    val cycleLengthDays: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        (preferences[cycleLengthDaysKey] ?: DEFAULT_CYCLE_LENGTH_DAYS).coerceIn(CycleCalculator.CYCLE_RANGE)
    }

    /** 经期长度（P9）：默认 5，范围 1–14；同样只作回退 / 覆盖值。 */
    val periodLengthDays: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        (preferences[periodLengthDaysKey] ?: DEFAULT_PERIOD_LENGTH_DAYS).coerceIn(PERIOD_LENGTH_RANGE)
    }

    suspend fun setCycleLengthDays(days: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[cycleLengthDaysKey] = days.coerceIn(CycleCalculator.CYCLE_RANGE)
        }
    }

    suspend fun setPeriodLengthDays(days: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[periodLengthDaysKey] = days.coerceIn(PERIOD_LENGTH_RANGE)
        }
    }

    /**
     * 进行中的经期起点（ISO 日期）；null = 当前没有进行中的经期。
     *
     * 只存「起点」这一个临时状态：区间 [起点..今天] 由派生显示，落库时按天补，
     * 所以不需要为每一天预先写记录，也不会出现「预测没更新、记录先涨」的漂移。
     * 不进备份（备份只有记录本体，恢复后最多丢失「进行中」这一临时态）。
     */
    val ongoingPeriodStart: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[ongoingPeriodStartKey]
    }

    suspend fun setOngoingPeriodStart(isoDate: String?) {
        context.settingsDataStore.edit { preferences ->
            if (isoDate == null) preferences.remove(ongoingPeriodStartKey)
            else preferences[ongoingPeriodStartKey] = isoDate
        }
    }

    /** 上次成功查询更新的时间（epoch millis）；0 表示从未成功查过。 */
    suspend fun lastUpdateCheckAt(): Long =
        context.settingsDataStore.data.first()[lastUpdateCheckAtKey] ?: 0L

    /** 上次查到的远端最新版本，用来判断节流会不会吞掉「有新版本」的提示。 */
    suspend fun lastKnownLatestVersion(): String? =
        context.settingsDataStore.data.first()[lastKnownLatestVersionKey]

    /**
     * 记一次成功的更新查询。只在拿到远端响应后调用——失败不写时间戳，
     * 下次启动会照常重试，不会因为一次断网把检查压后一整天。
     */
    suspend fun recordUpdateCheck(latestVersion: String?) {
        context.settingsDataStore.edit { preferences ->
            preferences[lastUpdateCheckAtKey] = System.currentTimeMillis()
            if (latestVersion != null) {
                preferences[lastKnownLatestVersionKey] = latestVersion
            }
        }
    }

    companion object {
        const val DEFAULT_CYCLE_LENGTH_DAYS = 28
        const val DEFAULT_PERIOD_LENGTH_DAYS = 5
        val PERIOD_LENGTH_RANGE = 1..14
    }
}
