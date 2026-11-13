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
import com.example.lifetrack.core.model.CycleCalculator
import com.example.lifetrack.core.model.CycleSnapshot
import com.example.lifetrack.core.model.DateRange
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PeriodFill
import com.example.lifetrack.core.model.PeriodOngoing
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.feature.sport.SportStats
import com.example.lifetrack.core.security.AppLockPolicy
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
    private val updateRepository: UpdateRepository
) : ViewModel() {
    private val todayDate = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()))

    /** 记录项目（启用状态 + 排序）。首次进入时补齐内置类型，之后由 Room Flow 驱动。 */
    val trackTypes: StateFlow<List<TrackTypeEntity>> = repository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.ensureDefaultTypes() }
        // 进行中的经期跨天自动延续：起点或日期一变，就把 [起点..昨天] 里缺的日子补进库。
        // 用「昨天」而不是「今天」，是为了不提前写还没过完的那一天，点「走了」时也不会留下多余记录。
        viewModelScope.launch {
            combine(
                settingsRepository.ongoingPeriodStart,
                todayDate
            ) { startIso, today -> startIso to today }
                .collect { (startIso, today) -> syncOngoingPeriod(startIso, today) }
        }
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

    /** 经期子应用：宽区间取月经记录，供周期推算、日历染色与简报；派生数据一律不落库。 */
    val periodRecords: StateFlow<List<BackupRecord>> = todayDate
        .flatMapLatest { today ->
            repository.observeRecordsInRange(
                today.minusYears(3).toString(),
                today.toString(),
                typeCode = MenstruationFields.TYPE_CODE
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 运动子应用：取数窗口与 [SportStats] 一致，供本周节奏、三类占比与简报派生；结果不落库。 */
    val sportRecords: StateFlow<List<BackupRecord>> = todayDate
        .flatMapLatest { today ->
            repository.observeRecordsInRange(
                SportStats.windowStart(today).toString(),
                today.toString(),
                typeCode = SportFields.TYPE_CODE
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 周期 / 经期长度（P9）：样本不足时的回退值与用户覆盖，预测结果本身不持久化。 */
    val cycleLengthDays: StateFlow<Int> = settingsRepository.cycleLengthDays
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.DEFAULT_CYCLE_LENGTH_DAYS
        )

    val periodLengthDays: StateFlow<Int> = settingsRepository.periodLengthDays
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.DEFAULT_PERIOD_LENGTH_DAYS
        )

    /**
     * 进行中的经期起点（null = 当前没有）。点「大姨妈来了」写入，点「大姨妈走了」清空。
     * 只存起点，区间 [起点..今天] 由 [PeriodOngoing.span] 派生显示，逐日落库见 [syncOngoingPeriod]。
     */
    val ongoingPeriodStart: StateFlow<LocalDate?> = settingsRepository.ongoingPeriodStart
        .map { iso -> iso?.let(::parseDateOrNull) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 记录或设置一变就重算周期快照；放在 Default 线程，避免主线程聚合掉帧。
     * 进行中的区间按「起点到今天」并入日期集合，所以状态条、日历、简报都自动跟上，
     * 不需要为还没过完的日子提前写记录。
     */
    val cycleSnapshot: StateFlow<CycleSnapshot> = combine(
        periodRecords,
        todayDate,
        cycleLengthDays,
        periodLengthDays,
        ongoingPeriodStart
    ) { records, today, cycleDays, periodDays, ongoingStart ->
        val dates = records.mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
        if (ongoingStart != null) dates += PeriodOngoing.span(ongoingStart, today)
        CycleCalculator.calculate(dates, today, cycleDays, periodDays)
    }.flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CycleCalculator.calculate(
                emptySet(),
                LocalDate.now(ZoneId.systemDefault()),
                SettingsRepository.DEFAULT_CYCLE_LENGTH_DAYS,
                SettingsRepository.DEFAULT_PERIOD_LENGTH_DAYS
            )
        )

    private val periodFeedbackChannel = Channel<String>(Channel.BUFFERED)

    /** 起止动作的一次性反馈（补齐 N 天 / 间隔过远），子应用收集后弹 Snackbar。 */
    val periodFeedback = periodFeedbackChannel.receiveAsFlow()

    /** 串行化经期写入：逐行即点即存与起止补齐可能并发，避免同一天重复落库。 */
    private val periodWriteMutex = Mutex()

    /**
     * 经期起止动作的「撤销上一步」（界面定稿 v2 · 批 2）。
     *
     * 只记住**本次动作自己新增的事件 id** 与动作前的进行中起点：撤销时精确删除这些
     * 仅标记记录、恢复起点，绝不按日期范围扫库，避免误删用户填过详情 / 备注的记录。
     * 状态只存在内存里，重进应用后可撤销状态自然消失（不落库、不跨会话承诺）。
     */
    private data class PeriodActionUndo(
        val addedEventIds: List<String>,
        val previousOngoingStart: LocalDate?,
        val label: String
    )

    private val lastPeriodAction = MutableStateFlow<PeriodActionUndo?>(null)
    val canUndoPeriodAction: StateFlow<Boolean> = lastPeriodAction
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
        observeStatistics(repository, todayDate, statsOffsets, statsTypeFilter)
            // 这里对 4 个周期做全窗口聚合（StatsAggregator.build），不加 flowOn 时
            // 它跑在 stateIn 的收集上下文，也就是 viewModelScope 默认的 Main；
            // 每写入一条记录 Room 都会重发一次，数据攒到几千条就会在主线程掉帧。
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyStatistics(todayDate.value)
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
    fun savePeriodDay(date: LocalDate, fields: Map<String, String>, note: String) {
        viewModelScope.launch {
            periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val existing = repository.observeRecordsInRange(
                        date.toString(), date.toString(), MenstruationFields.TYPE_CODE
                    ).first().firstOrNull()
                    val zone = runCatching {
                        ZoneId.of(existing?.event?.zoneId ?: ZoneId.systemDefault().id)
                    }.getOrDefault(ZoneId.systemDefault())
                    val millis = existing?.event?.occurredAtEpochMillis
                        ?: date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
                    val draft = RecordDraft(
                        typeCode = MenstruationFields.TYPE_CODE,
                        occurredAtEpochMillis = millis,
                        zoneId = zone.id,
                        note = note,
                        detail = if (fields.isEmpty()) {
                            null
                        } else {
                            RecordDetail.Generic(MenstruationFields.TYPE_CODE, fields)
                        }
                    )
                    if (existing == null) repository.addRecord(draft)
                    else repository.updateRecord(existing.event.id, draft)
                }
                refreshToday()
            }
        }
    }

    /**
     * 「大姨妈来了」：写当天一条仅标记事件（已有则不动），并记下进行中的起点。
     *
     * 起点到今天不超过 [PeriodOngoing.MAX_AUTO_DAYS] 天时进入「自动延续」：
     * 之后每天由 [syncOngoingPeriod] 补齐，日历整段染色，直到点「大姨妈走了」。
     * 更早的历史日期只标当天，避免一次误触把整月染成经期。
     */
    fun markPeriodStart(date: LocalDate) {
        viewModelScope.launch {
            val message = periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val today = LocalDate.now(ZoneId.systemDefault())
                    val addedEventIds = mutableListOf<String>()
                    val existing = repository.observeRecordsInRange(
                        date.toString(), date.toString(), MenstruationFields.TYPE_CODE
                    ).first().firstOrNull()
                    if (existing == null) addedEventIds += repository.addRecord(periodMarkDraft(date))

                    // 前一天已经记过（用户先逐日补了几天才点「来了」）时，进行中起点取当天所在
                    // 连续段的段首，保证状态条「第 N 天」、日历染色与点「走了」时补齐的起点一致；
                    // 段首与今天无关时（孤立记录 / 断段），才用选中的这一天。
                    val recentDates = repository.observeRecordsInRange(
                        today.minusYears(3).toString(), today.toString(), MenstruationFields.TYPE_CODE
                    ).first().mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
                    val segmentStart = CycleCalculator.segmentsOf(recentDates, today)
                        .firstOrNull { date in DateRange(it.start, it.end) }?.start
                        ?: date

                    val previousOngoingStart = settingsRepository.ongoingPeriodStart.first()?.let(::parseDateOrNull)
                    when {
                        previousOngoingStart != null && !date.isBefore(previousOngoingStart) ->
                            "经期进行中（第 ${ChronoUnit.DAYS.between(previousOngoingStart, today).toInt() + 1} 天），" +
                                "点「大姨妈走了」结束这一段"
                        previousOngoingStart != null ->
                            "${cnDate(previousOngoingStart)} 那一段还在进行中，先点「大姨妈走了」再记新的"
                        PeriodOngoing.canAutoContinue(date, today) -> {
                            settingsRepository.setOngoingPeriodStart(segmentStart.toString())
                            lastPeriodAction.value = PeriodActionUndo(
                                addedEventIds, previousOngoingStart, "开始经期 ${cnDate(date)}"
                            )
                            "经期已开始：之后每天自动标记，直到点「大姨妈走了」"
                        }
                        else -> {
                            lastPeriodAction.value = PeriodActionUndo(
                                addedEventIds, previousOngoingStart, "标记 ${cnDate(date)} 为经期日"
                            )
                            "已标记 ${cnDate(date)} 为经期日（距今超过 ${PeriodOngoing.MAX_AUTO_DAYS} 天，不自动延续）"
                        }
                    }
                }
            }
            refreshToday()
            periodFeedbackChannel.trySend(message)
        }
    }

    /**
     * 「大姨妈走了」：有进行中的起点时，把 `[起点..结束日]` 全部落库（仅标记，不覆盖已有详情）并清状态；
     * 没有进行中状态时，沿用原来的「从最近一段首日补齐」逻辑（兼容旧版本升级上来的数据）。
     * 间隔超过 [PeriodFill.MAX_GAP_DAYS] 天不自动补，返回提示要求逐日确认（设计 §5.2）。
     */
    fun markPeriodEnd(date: LocalDate) {
        viewModelScope.launch {
            val message = periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val today = LocalDate.now(ZoneId.systemDefault())
                    val addedEventIds = mutableListOf<String>()
                    val previousOngoingStart = settingsRepository.ongoingPeriodStart.first()?.let(::parseDateOrNull)
                    if (previousOngoingStart != null) {
                        if (date.isBefore(previousOngoingStart)) {
                            "结束日不能早于开始日（${cnDate(previousOngoingStart)}）"
                        } else {
                            val dates = PeriodOngoing.closingDates(previousOngoingStart, date)
                            val recorded = repository.observeRecordsInRange(
                                previousOngoingStart.toString(), date.toString(), MenstruationFields.TYPE_CODE
                            ).first().mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
                            dates.filterNot { it in recorded }
                                .forEach { addedEventIds += repository.addRecord(periodMarkDraft(it)) }
                            settingsRepository.setOngoingPeriodStart(null)
                            lastPeriodAction.value = PeriodActionUndo(
                                addedEventIds, previousOngoingStart, "结束这一段经期（${cnDate(date)}）"
                            )
                            "经期已结束：共 ${dates.size} 天（${cnDate(previousOngoingStart)} – ${cnDate(date)}）"
                        }
                    } else {
                        val records = repository.observeRecordsInRange(
                            date.minusYears(3).toString(), date.toString(), MenstruationFields.TYPE_CODE
                        ).first()
                        val dates = records.mapNotNullTo(mutableSetOf()) {
                            parseDateOrNull(it.event.localDate)
                        }
                        when (val plan = PeriodFill.plan(dates, date, today)) {
                            is PeriodFill.Plan.TooFar ->
                                "间隔超过 ${PeriodFill.MAX_GAP_DAYS} 天，请逐日确认后再补齐"
                            is PeriodFill.Plan.Fill -> {
                                plan.dates.forEach { addedEventIds += repository.addRecord(periodMarkDraft(it)) }
                                lastPeriodAction.value = PeriodActionUndo(
                                    addedEventIds, null, "补齐 ${plan.dates.size} 天经期记录"
                                )
                                "已标记结束，并补齐 ${plan.dates.size} 天（已有记录不覆盖）"
                            }
                        }
                    }
                }
            }
            refreshToday()
            periodFeedbackChannel.trySend(message)
        }
    }

    /**
     * 把进行中区间里缺的日子补进库（幂等，已有记录不覆盖）。
     * 只补到「昨天」：今天由派生显示，避免在用户还没过完这天时就先落库。
     * 写入走 [periodWriteMutex]，与起止动作、逐行保存串行，防止同一天重复插入。
     */
    private suspend fun syncOngoingPeriod(startIso: String?, today: LocalDate) {
        val start = startIso?.let(::parseDateOrNull) ?: return
        if (start.isAfter(today)) return
        periodWriteMutex.withLock {
            withContext(Dispatchers.IO) {
                val recorded = repository.observeRecordsInRange(
                    start.toString(), today.toString(), MenstruationFields.TYPE_CODE
                ).first().mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
                PeriodOngoing.pendingWrites(start, recorded, today)
                    .forEach { repository.addRecord(periodMarkDraft(it)) }
            }
        }
    }

    fun setCycleLengthDays(days: Int) {
        viewModelScope.launch { settingsRepository.setCycleLengthDays(days) }
    }

    fun setPeriodLengthDays(days: Int) {
        viewModelScope.launch { settingsRepository.setPeriodLengthDays(days) }
    }

    /**
     * 撤销最近一次起止动作（界面定稿 v2 · 批 2 的「撤销上一步」）。
     *
     * 只删除该动作自己新增的仅标记事件、恢复动作前的进行中起点；
     * 用户填过详情 / 备注的记录从不在 [PeriodActionUndo.addedEventIds] 里，因此不会被碰。
     */
    fun undoLastPeriodAction() {
        viewModelScope.launch {
            val action = lastPeriodAction.value ?: return@launch
            lastPeriodAction.value = null
            periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    action.addedEventIds.forEach { id ->
                        runCatching { repository.deletePoopRecord(id) }
                    }
                    settingsRepository.setOngoingPeriodStart(action.previousOngoingStart?.toString())
                }
            }
            refreshToday()
            periodFeedbackChannel.trySend("已撤销：${action.label}")
        }
    }

    /**
     * 「改开始日期」：把进行中的经期起点改到用户选的那天。
     *
     * 新起点晚于旧起点时，清掉被移出区间的**仅标记**日子（自动补齐出来的那些），
     * 用户手写过详情或备注的记录一律保留；新起点早于旧起点时不删任何记录，
     * 之后由 [syncOngoingPeriod] 自动补齐。改完仍可「撤销上一步」回到旧起点。
     */
    fun changePeriodStart(date: LocalDate) {
        viewModelScope.launch {
            val message = periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val today = LocalDate.now(ZoneId.systemDefault())
                    if (date.isAfter(today)) return@withContext "开始日期不能晚于今天"
                    val previous = settingsRepository.ongoingPeriodStart.first()?.let(::parseDateOrNull)
                    val addedEventIds = mutableListOf<String>()

                    if (previous != null && date.isAfter(previous)) {
                        val stale = repository.observeRecordsInRange(
                            previous.toString(), date.minusDays(1).toString(), MenstruationFields.TYPE_CODE
                        ).first().filter { it.detail == null && it.event.note.isBlank() }
                        stale.forEach { repository.deletePoopRecord(it.event.id) }
                    }
                    val existing = repository.observeRecordsInRange(
                        date.toString(), date.toString(), MenstruationFields.TYPE_CODE
                    ).first().firstOrNull()
                    if (existing == null) addedEventIds += repository.addRecord(periodMarkDraft(date))
                    settingsRepository.setOngoingPeriodStart(date.toString())
                    lastPeriodAction.value = PeriodActionUndo(
                        addedEventIds, previous, "开始日期改为 ${cnDate(date)}"
                    )
                    "开始日期已改为 ${cnDate(date)}"
                }
            }
            refreshToday()
            periodFeedbackChannel.trySend(message)
        }
    }

    /** 仅标记事件：detail = null、note 空，时刻取当天正午避开夏令时边界。 */
    private fun periodMarkDraft(date: LocalDate): RecordDraft {
        val zone = ZoneId.systemDefault()
        return RecordDraft(
            typeCode = MenstruationFields.TYPE_CODE,
            occurredAtEpochMillis = date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id,
            note = "",
            detail = null
        )
    }

    private fun parseDateOrNull(iso: String?): LocalDate? =
        iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun cnDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

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
