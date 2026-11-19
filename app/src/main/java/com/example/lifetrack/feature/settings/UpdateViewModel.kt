package com.example.lifetrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.update.ReleaseInfo
import com.example.lifetrack.core.update.UpdateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 应用更新入口；只负责把 UpdateRepository 暴露为页面状态与动作。 */
class UpdateViewModel(
    private val repository: UpdateRepository
) : ViewModel() {
    val updateState: StateFlow<UpdateRepository.Status> = repository.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UpdateRepository.Status.Idle
    )

    fun checkForUpdate(auto: Boolean = false) {
        viewModelScope.launch { repository.check(auto) }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        viewModelScope.launch { repository.downloadAndInstall(release) }
    }

    class Factory(
        private val repository: UpdateRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(UpdateViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return UpdateViewModel(repository) as T
        }
    }
}
