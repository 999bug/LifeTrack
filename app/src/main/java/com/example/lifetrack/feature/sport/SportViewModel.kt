package com.example.lifetrack.feature.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.domain.model.TrackType
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.repository.RecordRepository
import com.example.lifetrack.domain.repository.TypeCatalogRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class SportViewModel(
    private val recordRepository: RecordRepository,
    private val typeCatalogRepository: TypeCatalogRepository,
    private val todaySource: TodaySource
) : ViewModel() {
    val today: StateFlow<LocalDate> = todaySource.today

    val trackTypes: StateFlow<List<TrackType>> = typeCatalogRepository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sportRecords: StateFlow<List<BackupRecord>> = today
        .flatMapLatest { today ->
            recordRepository.observeRecordsInRange(
                SportStats.windowStart(today).toString(),
                today.toString(),
                typeCode = SportFields.TYPE_CODE
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    class Factory(
        private val recordRepository: RecordRepository,
    private val typeCatalogRepository: TypeCatalogRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(SportViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return SportViewModel(recordRepository, typeCatalogRepository, todaySource) as T
        }
    }
}
