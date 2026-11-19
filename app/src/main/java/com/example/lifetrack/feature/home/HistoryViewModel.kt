package com.example.lifetrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CalendarMonth
import com.example.lifetrack.core.model.HistoryFilter
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.repository.TrackRepository
import com.example.lifetrack.feature.history.DayCalendarData
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: TrackRepository,
    private val todaySource: TodaySource
) : ViewModel() {
    val today: StateFlow<LocalDate> = todaySource.today

    val trackTypes: StateFlow<List<TrackTypeEntity>> = repository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** 0 = 最近 30 天；每加载一页再往前多取 30 天。 */
    private val historyExtraPages = MutableStateFlow(0)
    private val historyFilter = MutableStateFlow<HistoryFilter?>(null)

    val historyQuery: StateFlow<HistoryQuery> = combine(
        historyFilter,
        historyExtraPages,
        today
    ) { filter, extraPages, today ->
        if (filter != null) {
            HistoryQuery(filter.start, filter.end, filtered = true)
        } else {
            HistoryQuery(today.minusDays(29L + extraPages * 30L), today, filtered = false)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialHistoryQuery()
    )

    val historyRecords: StateFlow<List<BackupRecord>> = historyQuery
        .flatMapLatest {
            // 历史列表默认展示全部类型：停用模块只影响新增入口，不影响已有记录的回看。
            repository.observeHistoryPage(it.start.toString(), it.end.toString(), typeCode = null, limit = (historyExtraPages.value + 1) * 100)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 月历的绝对偏移：0 = 本月，-1 = 上月；正数视为未来，直接钳回 0。 */
    private val calendarOffset = MutableStateFlow(0)

    /**
     * 月历格子。翻月后重新查该自然月的按天计数与按天类型集合，
     * 两个查询合并成一次渲染，避免「有次数但没图标」或反过来的中间态。
     * 计数口径为全部类型，与历史列表保持一致。
     */
    val calendar: StateFlow<CalendarMonth> = combine(today, calendarOffset) { today, offset ->
        CalendarMonth.of(today, offset)
    }.flatMapLatest { month ->
        combine(
            repository.observeDailyCounts(month.start.toString(), month.last.toString(), typeCode = null),
            repository.observeDailyTypeCounts(month.start.toString(), month.last.toString())
        ) { counts, typeRows ->
            month
                .withCounts(counts.associate { it.localDate to it.count })
                .withTypes(DayCalendarData.typesByDate(typeRows))
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarMonth.of(todaySource.currentDate(), 0)
    )

    /** 月历点开的日期；null 表示日期详情弹层未打开。 */
    private val dayDetailDate = MutableStateFlow<LocalDate?>(null)
    val dayDetail: StateFlow<LocalDate?> = dayDetailDate.asStateFlow()

    /** 弹层里的当天记录。只在弹层打开时查询，关闭后立即停止观察。 */
    val dayDetailRecords: StateFlow<List<BackupRecord>> = dayDetailDate
        .flatMapLatest { date ->
            if (date == null) {
                flowOf(emptyList())
            } else {
                repository.observeRecordsInRange(date.toString(), date.toString(), typeCode = null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 运动子应用：取数窗口与 [SportStats] 一致，供本周节奏、三类占比与简报派生；结果不落库。 */
    fun shiftCalendarMonth(steps: Int) {
        calendarOffset.update { (it + steps).coerceAtMost(0) }
    }

    /** 点月历某一格：打开当天详情。 */
    fun openDayDetail(date: LocalDate) {
        dayDetailDate.value = date
    }

    fun closeDayDetail() {
        dayDetailDate.value = null
    }

    fun loadEarlierHistory() {
        historyExtraPages.update { it + 1 }
    }

    fun setHistoryFilter(start: LocalDate, end: LocalDate) {
        historyFilter.value = HistoryFilter(minOf(start, end), maxOf(start, end))
    }

    fun clearHistoryFilter() {
        historyFilter.value = null
    }

    /** [passphrase] 为空时导出 v2 明文；非空时导出 v3 加密备份。 */
    private fun initialHistoryQuery(): HistoryQuery {
        val today = todaySource.currentDate()
        return HistoryQuery(today.minusDays(29), today, filtered = false)
    }


    class Factory(
        private val repository: TrackRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return HistoryViewModel(repository, todaySource) as T
        }
    }
}