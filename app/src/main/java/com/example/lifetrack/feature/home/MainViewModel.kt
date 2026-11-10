package com.example.lifetrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.backup.BackupCodec
import com.example.lifetrack.core.backup.BackupDecodeResult
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.backup.BackupReencryptResult
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.model.CalendarMonth
import com.example.lifetrack.core.update.ReleaseInfo
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.core.model.HistoryFilter
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.model.ModuleOrdering
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.statsWindow
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import com.example.lifetrack.feature.statistics.PeriodStats
import com.example.lifetrack.feature.statistics.StatsAggregator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TrackRepository,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository
) : ViewModel() {
    private val todayDate = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()))

    /** 记录项目（启用状态 + 排序）。首次进入时补齐内置类型，之后由 Room Flow 驱动。 */
    val trackTypes: StateFlow<List<TrackTypeEntity>> = repository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.ensureDefaultTypes() }
    }

    val todayRecords: StateFlow<List<BackupRecord>> = todayDate
        .flatMapLatest { repository.observeRecordsForDate(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0 = 最近 30 天；每加载一页再往前多取 30 天。 */
    private val historyExtraPages = MutableStateFlow(0)
    private val historyFilter = MutableStateFlow<HistoryFilter?>(null)

    val historyQuery: StateFlow<HistoryQuery> = combine(
        historyFilter,
        historyExtraPages,
        todayDate
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
            repository.observeRecordsInRange(it.start.toString(), it.end.toString(), typeCode = null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 月历的绝对偏移：0 = 本月，-1 = 上月；正数视为未来，直接钳回 0。 */
    private val calendarOffset = MutableStateFlow(0)

    /**
     * 月历格子。翻月后重新查该自然月的按天计数，
     * 复用已有的 observeDailyCounts，不新增统计区间。
     * 计数口径为全部类型，与历史列表保持一致。
     */
    val calendar: StateFlow<CalendarMonth> = combine(todayDate, calendarOffset) { today, offset ->
        CalendarMonth.of(today, offset)
    }.flatMapLatest { month ->
        repository
            .observeDailyCounts(month.start.toString(), month.last.toString(), typeCode = null)
            .map { counts -> month.withCounts(counts.associate { it.localDate to it.count }) }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarMonth.of(LocalDate.now(ZoneId.systemDefault()), 0)
    )

    /** 统计口径：null = 全部类型。统计页顶部的类型筛选直接写这里。 */
    private val statsTypeFilter = MutableStateFlow<String?>(null)
    val statsScope: StateFlow<String?> = statsTypeFilter.asStateFlow()

    /** 每个周期各自的翻页偏移：0 = 当前周期，-1 = 上一周期。 */
    private val statsOffsets = MutableStateFlow(StatsPeriod.entries.associateWith { 0 })

    /**
     * 三个周期的统计一次算完。
     * 查询区间覆盖「最远的上一周期起点 → 最远的本周期终点」，只用三次查询，
     * 避免每切一次周期就重新查库、出现两段加载态。
     * 类型筛选为空表示全部类型；形态分布只在排便口径下查询，避免与其他类型的总数串口径。
     */
    val statistics: StateFlow<Map<StatsPeriod, PeriodStats>> =
        combine(todayDate, statsOffsets, statsTypeFilter) { today, offsets, typeCode ->
            Triple(today, offsets, typeCode)
        }.flatMapLatest { (today, offsets, typeCode) ->
            val windows = StatsPeriod.entries.associateWith { period ->
                statsWindow(period, offsets[period] ?: 0, today)
            }
            val rangeStart = windows.values.minOf { it.previous.start }.toString()
            val rangeEnd = windows.values.maxOf { it.lastDate }.toString()
            val details = if (typeCode == DEFAULT_POOP_TYPE.code) {
                repository.observeStatDetails(rangeStart, rangeEnd)
            } else {
                flowOf(emptyList())
            }
            combine(
                repository.observeDailyCounts(rangeStart, rangeEnd, typeCode),
                repository.observeEventTimestamps(rangeStart, rangeEnd, typeCode),
                details
            ) { dailyCounts, timestamps, details ->
                windows.mapValues { (_, window) ->
                    StatsAggregator.build(
                        window = window,
                        today = today,
                        dailyCounts = dailyCounts,
                        timestamps = timestamps,
                        details = details
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyStatistics()
        )

    val darkTheme: StateFlow<Boolean> = settingsRepository.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val updateState: StateFlow<UpdateRepository.Status> =
        updateRepository.state.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UpdateRepository.Status.Idle
        )

    fun checkForUpdate(auto: Boolean = false) {
        viewModelScope.launch { updateRepository.check(auto) }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        viewModelScope.launch { updateRepository.downloadAndInstall(release) }
    }

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

    fun setStatsTypeFilter(typeCode: String?) {
        statsTypeFilter.value = typeCode
    }

    fun shiftCalendarMonth(steps: Int) {
        calendarOffset.update { (it + steps).coerceAtMost(0) }
    }

    fun setTypeEnabled(code: String, enabled: Boolean) {
        viewModelScope.launch { repository.setTypeEnabled(code, enabled) }
    }

    fun moveType(code: String, offset: Int) {
        val current = trackTypes.value
        val reordered = ModuleOrdering.move(current, code, offset)
        if (reordered != current) {
            viewModelScope.launch { repository.reorderTypes(reordered) }
        }
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
    suspend fun buildBackupJson(passphrase: CharArray? = null): String = withContext(Dispatchers.IO) {
        val payload = repository.readBackup()
        if (passphrase == null) {
            BackupCodec.encode(payload.types, payload.events)
        } else {
            BackupCodec.encodeEncrypted(payload.types, payload.events, passphrase)
        }
    }

    /** 未给口令时，加密备份会返回 `PassphraseRequired`，由设置页弹口令输入。 */
    suspend fun decodeBackupJson(
        text: String,
        passphrase: CharArray? = null
    ): BackupDecodeResult = withContext(Dispatchers.IO) {
        BackupCodec.decode(text, passphrase = passphrase)
    }

    /** 口令轮换：旧口令解出、新口令重加密，返回完整的新文件内容。 */
    suspend fun reencryptBackup(
        text: String,
        oldPassphrase: CharArray,
        newPassphrase: CharArray
    ): BackupReencryptResult = withContext(Dispatchers.IO) {
        BackupCodec.reencrypt(text, oldPassphrase, newPassphrase)
    }

    suspend fun restoreBackup(payload: BackupPayload) {
        withContext(Dispatchers.IO) { repository.restoreBackup(payload) }
        refreshToday()
    }

    suspend fun countAllRecords(): Int = withContext(Dispatchers.IO) {
        repository.countAllRecords()
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

    /** 新类型（饮水等）走通用详情表；排便仍走上面的强类型路径。 */
    fun saveGenericRecord(eventId: String?, draft: RecordDraft) {
        viewModelScope.launch {
            if (eventId == null) repository.addRecord(draft)
            else repository.updateRecord(eventId, draft)
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

    private fun initialHistoryQuery(): HistoryQuery {
        val today = LocalDate.now(ZoneId.systemDefault())
        return HistoryQuery(today.minusDays(29), today, filtered = false)
    }

    class Factory(
        private val repository: TrackRepository,
        private val settingsRepository: SettingsRepository,
        private val updateRepository: UpdateRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return MainViewModel(repository, settingsRepository, updateRepository) as T
        }
    }
}
