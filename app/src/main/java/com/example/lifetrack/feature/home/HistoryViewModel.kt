package com.example.lifetrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.HistoryFilter
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.model.HistoryPage
import com.example.lifetrack.domain.model.RecordCursor
import com.example.lifetrack.domain.model.TrackType
import com.example.lifetrack.domain.repository.RecordRepository
import com.example.lifetrack.domain.repository.StatisticsRepository
import com.example.lifetrack.domain.repository.TypeCatalogRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val HISTORY_PAGE_SIZE = 100
private const val HISTORY_RANGE_STEP_DAYS = 30L

/** 一页查询结果及其游标；页面只消费展平后的记录，测试可直接验证分页状态。 */
internal data class HistoryPagesState(
    val query: HistoryQuery,
    val pages: List<HistoryPage>
) {
    val records: List<BackupRecord>
        get() = pages.flatMap { it.records }.distinctBy { it.event.id }

    val canLoadEarlier: Boolean
        get() {
            val last = pages.lastOrNull() ?: return false
            if (last.records.isEmpty()) return false
            return if (query.filtered) last.hasMore else true
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val recordRepository: RecordRepository,
    private val statisticsRepository: StatisticsRepository,
    private val typeCatalogRepository: TypeCatalogRepository,
    private val todaySource: TodaySource
) : ViewModel() {
    val today: StateFlow<LocalDate> = todaySource.today

    val trackTypes: StateFlow<List<TrackType>> = typeCatalogRepository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0 = 最近 30 天；每次加载更早时只扩展查询区间，不重复读取已加载页。 */
    private val historyExtraPages = MutableStateFlow(0)
    private val historyFilter = MutableStateFlow<HistoryFilter?>(null)
    private val pageCursors = MutableStateFlow<List<RecordCursor?>>(listOf(null))

    val historyQuery: StateFlow<HistoryQuery> = combine(
        historyFilter,
        historyExtraPages,
        today
    ) { filter, extraPages, today ->
        if (filter != null) {
            HistoryQuery(filter.start, filter.end, filtered = true)
        } else {
            HistoryQuery(
                start = today.minusDays(29L + extraPages * HISTORY_RANGE_STEP_DAYS),
                end = today,
                filtered = false
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialHistoryQuery()
    )

    internal val historyPages: StateFlow<HistoryPagesState> = combine(
        historyQuery,
        pageCursors
    ) { query, cursors ->
        query to cursors
    }.flatMapLatest { (query, cursors) ->
        if (cursors.isEmpty()) {
            flowOf(HistoryPagesState(query, emptyList()))
        } else {
            combine(
                cursors.map { cursor ->
                    recordRepository.observeHistoryPage(
                        startDate = query.start.toString(),
                        endDate = query.end.toString(),
                        typeCode = null,
                        cursor = cursor,
                        limit = HISTORY_PAGE_SIZE
                    )
                }
            ) { pages ->
                HistoryPagesState(query, pages.toList())
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryPagesState(initialHistoryQuery(), emptyList())
    )

    val historyRecords: StateFlow<List<BackupRecord>> = historyPages
        .map { it.records }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canLoadEarlier: StateFlow<Boolean> = historyPages
        .map { it.canLoadEarlier }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 月历的绝对偏移：0 = 本月，-1 = 上月；正数视为未来，直接钳回 0。 */
    private val calendarOffset = MutableStateFlow(0)

    val calendar: StateFlow<com.example.lifetrack.core.model.CalendarMonth> = combine(today, calendarOffset) { day, offset ->
        com.example.lifetrack.core.model.CalendarMonth.of(day, offset)
    }.flatMapLatest { month ->
        combine(
            statisticsRepository.observeDailyCounts(
                month.start.toString(),
                month.last.toString(),
                typeCode = null
            ),
            statisticsRepository.observeDailyTypeCounts(month.start.toString(), month.last.toString())
        ) { counts, typeRows ->
            month
                .withCounts(counts.associate { it.localDate to it.count })
                .withTypes(DayCalendarData.typesByDate(typeRows))
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.lifetrack.core.model.CalendarMonth.of(todaySource.currentDate(), 0)
    )

    private val dayDetailDate = MutableStateFlow<LocalDate?>(null)
    val dayDetail: StateFlow<LocalDate?> = dayDetailDate.asStateFlow()

    val dayDetailRecords: StateFlow<List<BackupRecord>> = dayDetailDate
        .flatMapLatest { date ->
            if (date == null) {
                flowOf(emptyList())
            } else {
                recordRepository.observeRecordsInRange(
                    date.toString(),
                    date.toString(),
                    typeCode = null
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shiftCalendarMonth(steps: Int) {
        calendarOffset.update { (it + steps).coerceAtMost(0) }
    }

    fun openDayDetail(date: LocalDate) {
        dayDetailDate.value = date
    }

    fun closeDayDetail() {
        dayDetailDate.value = null
    }

    /** 追加严格 keyset 游标；未筛选时同时扩大日期窗口，避免只能看到最初 30 天。 */
    fun loadEarlierHistory() {
        val state = historyPages.value
        val lastPage = state.pages.lastOrNull() ?: return
        val cursor = lastPage.nextCursor ?: return
        if (lastPage.records.isEmpty()) return
        if (state.query.filtered && !lastPage.hasMore) return
        if (!state.query.filtered) {
            historyExtraPages.update { it + 1 }
        }
        pageCursors.update { it + cursor }
    }

    fun setHistoryFilter(start: LocalDate, end: LocalDate) {
        historyExtraPages.value = 0
        pageCursors.value = listOf(null)
        historyFilter.value = HistoryFilter(minOf(start, end), maxOf(start, end))
    }

    fun clearHistoryFilter() {
        historyExtraPages.value = 0
        pageCursors.value = listOf(null)
        historyFilter.value = null
    }

    private fun initialHistoryQuery(): HistoryQuery {
        val today = todaySource.currentDate()
        return HistoryQuery(today.minusDays(29), today, filtered = false)
    }

    class Factory(
        private val recordRepository: RecordRepository,
        private val statisticsRepository: StatisticsRepository,
        private val typeCatalogRepository: TypeCatalogRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return HistoryViewModel(
                recordRepository,
                statisticsRepository,
                typeCatalogRepository,
                todaySource
            ) as T
        }
    }
}
