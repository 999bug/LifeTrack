package com.example.lifetrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.database.model.PoopRecordRelation
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.statsWindow
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import com.example.lifetrack.feature.statistics.PeriodStats
import com.example.lifetrack.feature.statistics.StatsAggregator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TrackRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val todayDate = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()))

    val todayRecords: StateFlow<List<PoopRecordRelation>> = todayDate
        .flatMapLatest { repository.observePoopRecordsForDate(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val historyEnd = LocalDate.now(ZoneId.systemDefault())
    val historyRecords: StateFlow<List<PoopRecordRelation>> = repository
        .observePoopRecordsInRange(historyEnd.minusDays(29).toString(), historyEnd.toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 每个周期各自的翻页偏移：0 = 当前周期，-1 = 上一周期。 */
    private val statsOffsets = MutableStateFlow(StatsPeriod.entries.associateWith { 0 })

    /**
     * 三个周期的统计一次算完。
     * 查询区间覆盖「最远的上一周期起点 → 最远的本周期终点」，只用两次查询，
     * 避免每切一次周期就重新查库、出现两段加载态。
     */
    val statistics: StateFlow<Map<StatsPeriod, PeriodStats>> = combine(todayDate, statsOffsets) { today, offsets ->
        today to StatsPeriod.entries.associateWith { period ->
            statsWindow(period, offsets[period] ?: 0, today)
        }
    }.flatMapLatest { (today, windows) ->
        val rangeStart = windows.values.minOf { it.previous.start }.toString()
        val rangeEnd = windows.values.maxOf { it.lastDate }.toString()
        combine(
            repository.observeDailyCounts(rangeStart, rangeEnd),
            repository.observeEventTimestamps(rangeStart, rangeEnd)
        ) { dailyCounts, timestamps ->
            windows.mapValues { (_, window) ->
                StatsAggregator.build(window, today, dailyCounts, timestamps)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyStatistics()
    )

    val darkTheme: StateFlow<Boolean> = settingsRepository.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refreshToday() {
        todayDate.value = LocalDate.now(ZoneId.systemDefault())
    }

    fun shiftStatistics(period: StatsPeriod, steps: Int) {
        statsOffsets.update { current ->
            val currentOffset = current[period] ?: 0
            val next = (currentOffset + steps).coerceAtMost(0)
            if (next == currentOffset) current else current + (period to next)
        }
    }

    fun quickRecord() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            repository.addPoopRecord(
                PoopRecordDraft(
                    occurredAtEpochMillis = Instant.now().toEpochMilli(),
                    zoneId = zone.id
                )
            )
            refreshToday()
        }
    }

    fun saveRecord(eventId: String?, draft: PoopRecordDraft) {
        viewModelScope.launch {
            if (eventId == null) repository.addPoopRecord(draft)
            else repository.updatePoopRecord(eventId, draft)
            refreshToday()
        }
    }

    fun deleteRecord(eventId: String) {
        viewModelScope.launch {
            repository.deletePoopRecord(eventId)
            refreshToday()
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    }

    private fun emptyStatistics(): Map<StatsPeriod, PeriodStats> {
        val today = LocalDate.now(ZoneId.systemDefault())
        return StatsPeriod.entries.associateWith { period ->
            StatsAggregator.build(statsWindow(period, 0, today), today, emptyList(), emptyList())
        }
    }

    class Factory(
        private val repository: TrackRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return MainViewModel(repository, settingsRepository) as T
        }
    }
}
