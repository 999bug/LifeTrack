package com.example.lifetrack.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.model.AppLockTimeout
import com.example.lifetrack.core.security.AppLockPolicy
import com.example.lifetrack.core.time.TodaySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 应用锁状态与前后台生命周期策略。 */
class AppLockViewModel(
    private val settingsRepository: SettingsRepository,
    private val todaySource: TodaySource
) : ViewModel() {
    /** null 表示设置尚未从 DataStore 读出；此时不能先渲染正文，避免锁屏前闪一下内容。 */
    val appLockEnabled: StateFlow<Boolean?> = settingsRepository.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val appLockTimeout: StateFlow<AppLockTimeout> = settingsRepository.appLockTimeout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockTimeout.IMMEDIATE)

    private var lastBackgroundAtMillis: Long? = null
    private val appLockedState = MutableStateFlow<Boolean?>(null)

    /** null 表示设置尚未读出或尚未判定；true 时必须先通过系统认证才渲染正文。 */
    val appLocked: StateFlow<Boolean?> = appLockedState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appLockEnabled.collect { enabled ->
                if (!enabled) {
                    appLockedState.value = false
                    lastBackgroundAtMillis = null
                } else if (appLockedState.value == null) {
                    // 冷启动或进程被杀后重建：只要设置里开着，先锁再谈其他。
                    appLockedState.value = true
                }
            }
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    fun setAppLockTimeout(timeout: AppLockTimeout) {
        viewModelScope.launch { settingsRepository.setAppLockTimeout(timeout) }
    }

    /** 由 Activity 的 ON_STOP 调用：记录退到后台的时间。 */
    fun onAppBackgrounded() {
        if (appLockEnabled.value == true) {
            lastBackgroundAtMillis = System.currentTimeMillis()
        }
    }

    /** 由 Activity 的 ON_START 调用：先校正业务日期，再按超时策略决定是否重新上锁。 */
    fun onAppForegrounded() {
        todaySource.refresh()
        // 设置还没读出来时不要解锁：null 不等于「已关闭」。
        val enabled = appLockEnabled.value ?: return
        if (!enabled) {
            appLockedState.value = false
            return
        }
        if (AppLockPolicy.shouldLock(
                timeout = appLockTimeout.value,
                lastBackgroundAtMillis = lastBackgroundAtMillis,
                nowMillis = System.currentTimeMillis()
            )
        ) {
            appLockedState.value = true
        }
    }

    fun unlockApp() {
        appLockedState.value = false
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(AppLockViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return AppLockViewModel(settingsRepository, todaySource) as T
        }
    }
}
