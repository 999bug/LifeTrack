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
import com.example.lifetrack.core.model.AppLockTimeout
import com.example.lifetrack.core.update.ReleaseInfo
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.core.model.HistoryFilter
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.model.ModuleOrdering
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.ThemeMode
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.model.statsWindow
import com.example.lifetrack.core.security.AppLockPolicy
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import com.example.lifetrack.feature.history.DayCalendarData
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 首页饮水卡一键记录的默认杯量（ml）。 */
private const val QUICK_WATER_AMOUNT_ML = 250

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
     * 月历格子。翻月后重新查该自然月的按天计数与按天类型集合，
     * 两个查询合并成一次渲染，避免「有次数但没图标」或反过来的中间态。
     * 计数口径为全部类型，与历史列表保持一致。
     */
    val calendar: StateFlow<CalendarMonth> = combine(todayDate, calendarOffset) { today, offset ->
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
        CalendarMonth.of(LocalDate.now(ZoneId.systemDefault()), 0)
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
        }
            // 这里对 4 个周期做全窗口聚合（StatsAggregator.build），不加 flowOn 时
            // 它跑在 stateIn 的收集上下文，也就是 viewModelScope 默认的 Main；
            // 每写入一条记录 Room 都会重发一次，数据攒到几千条就会在主线程掉帧。
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyStatistics()
            )

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)

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

    /** 点月历某一格：打开当天详情。 */
    fun openDayDetail(date: LocalDate) {
        dayDetailDate.value = date
    }

    fun closeDayDetail() {
        dayDetailDate.value = null
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

    /**
     * 首页饮水卡的一键记录：默认一杯 250 ml。
     * 与排便一样属于「有合理默认值」的动作，因此不在首页弹编辑器；
     * 需要改份量时走卡片上的「详细」。
     */
    fun quickWaterRecord() {
        viewModelScope.launch {
            repository.addRecord(
                RecordDraft(
                    typeCode = WaterFields.TYPE_CODE,
                    occurredAtEpochMillis = Instant.now().toEpochMilli(),
                    zoneId = ZoneId.systemDefault().id,
                    detail = RecordDetail.Generic(
                        typeCode = WaterFields.TYPE_CODE,
                        fields = mapOf(
                            WaterFields.AMOUNT to QUICK_WATER_AMOUNT_ML.toString(),
                            WaterFields.UNIT to "ml"
                        )
                    )
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
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

    /** 由 Activity 的 ON_START 调用：按超时策略决定是否重新上锁。 */
    fun onAppForegrounded() {
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
