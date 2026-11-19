package com.example.lifetrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.backup.BackupCodec
import com.example.lifetrack.core.backup.BackupDecodeResult
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.backup.BackupReencryptResult
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.model.CalendarMonth
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.HistoryFilter
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.model.ModuleOrdering
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.ThemeMode
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.model.CycleCalculator
import com.example.lifetrack.core.model.CycleSnapshot
import com.example.lifetrack.core.model.DateRange
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.core.model.PeriodEndEdit
import com.example.lifetrack.core.model.PeriodFill
import com.example.lifetrack.core.model.PeriodOngoing
import com.example.lifetrack.core.model.PeriodStartEdit
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.feature.sport.SportStats
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import com.example.lifetrack.feature.history.DayCalendarData
import com.example.lifetrack.feature.statistics.PeriodStats
import com.example.lifetrack.feature.statistics.emptyStatistics
import com.example.lifetrack.feature.statistics.observeStatistics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 首页饮水卡一键记录的默认杯量（ml）。 */
private const val QUICK_WATER_AMOUNT_ML = 250

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TrackRepository,
    private val settingsRepository: SettingsRepository,
    private val todaySource: TodaySource
) : ViewModel() {

    /** 应用内唯一的业务日期；页面不得再自行调用 LocalDate.now()。 */
    val today: StateFlow<LocalDate> = todaySource.today

    /** 记录项目（启用状态 + 排序）。首次进入时补齐内置类型，之后由 Room Flow 驱动。 */
    val trackTypes: StateFlow<List<TrackTypeEntity>> = repository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // 跨午夜时主动刷新业务日期，避免前台长时间停留后继续查询昨天。
            while (isActive) {
                delay(todaySource.millisUntilNextDay())
                refreshToday()
            }
        }
        viewModelScope.launch {
            // 先补齐内置类型（含 tea），再把旧版 drinkType=tea 的记录迁到 tea 类型。
            // 迁移幂等，没有旧记录时不写库；放在同一协程里保证顺序。
            repository.ensureDefaultTypes()
            repository.migrateLegacyTeaRecords()
        }

    }

    val todayRecords: StateFlow<List<BackupRecord>> = today
        .flatMapLatest { repository.observeRecordsForDate(it.toString()) }
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
    val sportRecords: StateFlow<List<BackupRecord>> = today
        .flatMapLatest { today ->
            repository.observeRecordsInRange(
                SportStats.windowStart(today).toString(),
                today.toString(),
                typeCode = SportFields.TYPE_CODE
            )
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
        observeStatistics(repository, today, statsOffsets, statsTypeFilter)
            // 这里对 4 个周期做全窗口聚合（StatsAggregator.build），不加 flowOn 时
            // 它跑在 stateIn 的收集上下文，也就是 viewModelScope 默认的 Main；
            // 每写入一条记录 Room 都会重发一次，数据攒到几千条就会在主线程掉帧。
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyStatistics(today.value)
            )

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)

    /** 用户自定义的咖啡 / 茶品种（预设品种另在 CoffeeFields/TeaFields 里固定）。 */
    val customCoffeeVarieties: StateFlow<List<String>> = settingsRepository.customCoffeeVarieties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customTeaVarieties: StateFlow<List<String>> = settingsRepository.customTeaVarieties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCustomCoffeeVariety(name: String) {
        viewModelScope.launch { settingsRepository.addCustomCoffeeVariety(name) }
    }

    fun addCustomTeaVariety(name: String) {
        viewModelScope.launch { settingsRepository.addCustomTeaVariety(name) }
    }

    fun removeCustomCoffeeVariety(name: String) {
        viewModelScope.launch { settingsRepository.removeCustomCoffeeVariety(name) }
    }

    fun removeCustomTeaVariety(name: String) {
        viewModelScope.launch { settingsRepository.removeCustomTeaVariety(name) }
    }

    fun refreshToday() {
        todaySource.refresh()
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
        // 恢复是整体替换：旧的「进行中起点」可能指向已不存在的数据，直接清掉，
        // 让用户在新数据上自己决定要不要重新点「大姨妈来了」。
        settingsRepository.setOngoingPeriodStart(null)
        refreshToday()
    }

    suspend fun countAllRecords(): Int = withContext(Dispatchers.IO) {
        repository.countAllRecords()
    }

    /** 「整理数据库」的状态。只在设置页数据区使用，是一次性维护动作。 */
    sealed interface CompactState {
        data object Idle : CompactState
        data object Running : CompactState

        /** 整理前后的文件大小，用来告诉用户实际回收了多少。 */
        data class Done(val beforeBytes: Long, val afterBytes: Long) : CompactState
        data class Failed(val message: String) : CompactState
    }

    private val _compactState = MutableStateFlow<CompactState>(CompactState.Idle)
    val compactState: StateFlow<CompactState> = _compactState.asStateFlow()

    /** 当前数据库文件大小；读不到时返回 0，UI 显示「—」。 */
    suspend fun databaseSizeBytes(): Long =
        runCatching { repository.databaseSizeBytes() }.getOrDefault(0L)

    fun compactDatabase() {
        if (_compactState.value is CompactState.Running) return
        viewModelScope.launch {
            _compactState.value = CompactState.Running
            val before = runCatching { repository.databaseSizeBytes() }.getOrDefault(0L)
            runCatching { repository.compactDatabase() }
                .onSuccess { after -> _compactState.value = CompactState.Done(before, after) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _compactState.value = CompactState.Failed(e.message ?: "整理失败")
                }
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

    private val poopFeedbackChannel = Channel<String>(Channel.BUFFERED)

    /** 列表里「结束」的一次性反馈（用时已记录 / 超过阈值未记录），首页收集后弹 Snackbar。 */
    val poopFeedback = poopFeedbackChannel.receiveAsFlow()

    /**
     * 在今日列表里直接结束一条进行中的排便记录：把「现在」写成结束时间并算出用时。
     *
     * 用时口径全部交给 [PoopFields.resolve]：超过 60 分钟不写用时（记录本身保留），
     * 只回一条提示让用户改用手填。已有用时的记录不再重复结束，避免把历史值覆盖成 0。
     */
    fun endPoopRecord(record: BackupRecord) {
        val detail = record.detail as? RecordDetail.Poop
        if (detail?.durationMinutes != null) return
        viewModelScope.launch {
            val timing = PoopFields.resolve(
                startEpochMillis = record.event.occurredAtEpochMillis,
                endedAtEpochMillis = System.currentTimeMillis(),
                manualMinutes = ""
            )
            repository.updatePoopRecord(
                eventId = record.event.id,
                draft = PoopRecordDraft(
                    occurredAtEpochMillis = record.event.occurredAtEpochMillis,
                    zoneId = record.event.zoneId,
                    bristolType = detail?.bristolType,
                    amountCode = detail?.amountCode,
                    effortCode = detail?.effortCode,
                    urgency = detail?.urgency,
                    abdominalDiscomfort = detail?.abdominalDiscomfort,
                    incompleteEvacuation = detail?.incompleteEvacuation,
                    note = record.event.note,
                    endedAtEpochMillis = timing.endedAtEpochMillis,
                    durationMinutes = timing.durationMinutes
                )
            )
            refreshToday()
            poopFeedbackChannel.trySend(
                timing.durationMinutes?.let { "本次用时 $it 分钟" }
                    ?: "已超过 ${PoopFields.MAX_DURATION_MINUTES} 分钟，用时未记录；可在编辑里手动填写"
            )
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

    /**
     * 子应用逐行填写：把选中日的体征 + 日记 upsert 成一条月经事件，按 `(typeCode, localDate)` 幂等。
     * [fields] 已由 [com.example.lifetrack.core.model.PeriodDayFormModel.toFields] 收敛（空键不写、症状编码），
     * 全空时落 `detail = null` 的仅标记事件，与恢复路径一致。
     */
    fun deleteRecord(eventId: String) {
        viewModelScope.launch {
            repository.deletePoopRecord(eventId)
            refreshToday()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    private fun initialHistoryQuery(): HistoryQuery {
        val today = todaySource.currentDate()
        return HistoryQuery(today.minusDays(29), today, filtered = false)
    }

    class Factory(
        private val repository: TrackRepository,
        private val settingsRepository: SettingsRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return MainViewModel(repository, settingsRepository, todaySource) as T
        }
    }
}
