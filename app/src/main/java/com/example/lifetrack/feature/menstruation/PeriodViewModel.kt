package com.example.lifetrack.feature.menstruation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.ui.ActionFeedback
import com.example.lifetrack.core.ui.launchAction
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.domain.model.TrackType
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CycleCalculator
import com.example.lifetrack.core.model.CycleSnapshot
import com.example.lifetrack.core.model.DateRange
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PeriodEndEdit
import com.example.lifetrack.core.model.PeriodFill
import com.example.lifetrack.core.model.PeriodOngoing
import com.example.lifetrack.core.model.PeriodStartEdit
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.RecordRepository
import com.example.lifetrack.domain.repository.TypeCatalogRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodViewModel(
    private val recordRepository: RecordRepository,
    private val typeCatalogRepository: TypeCatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val todaySource: TodaySource
) : ViewModel() {
    val actionFeedback = ActionFeedback()
    val actionMessages = actionFeedback.messages

    val today: StateFlow<LocalDate> = todaySource.today

    val trackTypes: StateFlow<List<TrackType>> = typeCatalogRepository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** 经期子应用：宽区间取月经记录，供周期推算、日历染色与简报；派生数据一律不落库。 */
    val periodRecords: StateFlow<List<BackupRecord>> = today
        .flatMapLatest { today ->
            recordRepository.observeRecordsInRange(
                today.minusYears(3).toString(),
                today.toString(),
                typeCode = MenstruationFields.TYPE_CODE
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 亲密关系（重构后收进经期子应用）：取数区间与 [periodRecords] 对齐，
     * 供选中日的记录列表与「上次」措施标记使用；记录本身仍是独立事件，不并进月经详情。
     */
    val intimacyRecords: StateFlow<List<BackupRecord>> = today
        .flatMapLatest { today ->
            recordRepository.observeRecordsInRange(
                today.minusYears(3).toString(),
                today.toString(),
                typeCode = IntimacyFields.TYPE_CODE
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
        today,
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
                todaySource.currentDate(),
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
     * 子应用逐行填写：把选中日的体征 + 日记 upsert 成一条月经事件，按 `(typeCode, localDate)` 幂等。
     * [fields] 已由 [com.example.lifetrack.core.model.PeriodDayFormModel.toFields] 收敛（空键不写、症状编码），
     * 全空时落 `detail = null` 的仅标记事件，与恢复路径一致。
     */
    fun savePeriodDay(date: LocalDate, fields: Map<String, String>, note: String) {
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val existing = recordRepository.observeRecordsInRange(
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
                    if (existing == null) recordRepository.addRecord(draft)
                    else recordRepository.updateRecord(existing.event.id, draft)
                }
                todaySource.refresh()
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
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            val message = periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val today = todaySource.currentDate()
                    val existing = recordRepository.observeRecordsInRange(
                        date.toString(), date.toString(), MenstruationFields.TYPE_CODE
                    ).first().firstOrNull()
                    if (existing == null) recordRepository.addRecord(periodMarkDraft(date))

                    // 前一天已经记过（用户先逐日补了几天才点「来了」）时，进行中起点取当天所在
                    // 连续段的段首，保证状态条「第 N 天」、日历染色与点「走了」时补齐的起点一致；
                    // 段首与今天无关时（孤立记录 / 断段），才用选中的这一天。
                    val recentDates = recordRepository.observeRecordsInRange(
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
                            "经期已开始：之后每天自动标记，直到点「大姨妈走了」"
                        }
                        else -> {
                            "已标记 ${cnDate(date)} 为经期日（距今超过 ${PeriodOngoing.MAX_AUTO_DAYS} 天，不自动延续）"
                        }
                    }
                }
            }
            todaySource.refresh()
            periodFeedbackChannel.trySend(message)
        }
    }

    /**
     * 「大姨妈走了」：有进行中的起点时，把 `[起点..结束日]` 全部落库（仅标记，不覆盖已有详情）并清状态；
     * 没有进行中状态时，沿用原来的「从最近一段首日补齐」逻辑（兼容旧版本升级上来的数据）。
     * 间隔超过 [PeriodFill.MAX_GAP_DAYS] 天不自动补，返回提示要求逐日确认（设计 §5.2）。
     *
     * 结束日早于「今天」时，[previousOngoingStart] 之后自动延续写下的日子还在库里：
     * 这里顺手把结束日之后的**仅标记**记录撤掉，否则用户选早了结束日，界面上结束日也退不回去。
     */
    fun markPeriodEnd(date: LocalDate) {
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            val message = periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val today = todaySource.currentDate()
                    val previousOngoingStart = settingsRepository.ongoingPeriodStart.first()?.let(::parseDateOrNull)
                    if (previousOngoingStart != null) {
                        if (date.isBefore(previousOngoingStart)) {
                            "结束日不能早于开始日（${cnDate(previousOngoingStart)}）"
                        } else {
                            val dates = PeriodOngoing.closingDates(previousOngoingStart, date)
                            val records = recordRepository.observeRecordsInRange(
                                previousOngoingStart.toString(), today.toString(), MenstruationFields.TYPE_CODE
                            ).first()
                            val recorded = records.mapNotNullTo(mutableSetOf()) {
                                parseDateOrNull(it.event.localDate)
                            }
                            dates.filterNot { it in recorded }
                                .forEach { recordRepository.addRecord(periodMarkDraft(it)) }
                            // 结束日之后由「自动延续」写下的仅标记日子撤掉；填过内容的一天原样保留。
                            val trailing = records.filter { record ->
                                val day = parseDateOrNull(record.event.localDate)
                                day != null && day.isAfter(date)
                            }
                            val trimmable = trailing.filter { isMarkOnly(it) }
                            val keptTrailing = trailing.size - trimmable.size
                            trimmable.forEach { recordRepository.deletePoopRecord(it.event.id) }
                            val removed = trimmable.size
                            settingsRepository.setOngoingPeriodStart(null)
                            buildString {
                                append("经期已结束：共 ${dates.size} 天（${cnDate(previousOngoingStart)} – ${cnDate(date)}）")
                                if (removed > 0) append("，撤销了之后 $removed 天")
                                if (keptTrailing > 0) append("（$keptTrailing 天填过内容已保留）")
                            }
                        }
                    } else {
                        val records = recordRepository.observeRecordsInRange(
                            date.minusYears(3).toString(), date.toString(), MenstruationFields.TYPE_CODE
                        ).first()
                        val dates = records.mapNotNullTo(mutableSetOf()) {
                            parseDateOrNull(it.event.localDate)
                        }
                        when (val plan = PeriodFill.plan(dates, date, today)) {
                            is PeriodFill.Plan.TooFar ->
                                "间隔超过 ${PeriodFill.MAX_GAP_DAYS} 天，请逐日确认后再补齐"
                            is PeriodFill.Plan.Fill -> {
                                plan.dates.forEach { recordRepository.addRecord(periodMarkDraft(it)) }
                                "已标记结束，并补齐 ${plan.dates.size} 天（已有记录不覆盖）"
                            }
                        }
                    }
                }
            }
            todaySource.refresh()
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
                val recorded = recordRepository.observeRecordsInRange(
                    start.toString(), today.toString(), MenstruationFields.TYPE_CODE
                ).first().mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
                PeriodOngoing.pendingWrites(start, recorded, today)
                    .forEach { recordRepository.addRecord(periodMarkDraft(it)) }
            }
        }
    }

    fun setCycleLengthDays(days: Int) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { settingsRepository.setCycleLengthDays(days) }
    }

    fun setPeriodLengthDays(days: Int) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { settingsRepository.setPeriodLengthDays(days) }
    }

    /**
     * 「设为开始日」：把最近一段经期的首日挪到用户点的那一天。
     *
     * 首日由记录派生，所以只能落到记录层面：把 `[旧首日..新首日-1]` 里由系统自动标记出来的
     * 日子撤掉，首日自然前移。差异由 [PeriodStartEdit.plan] 算出。
     *
     * 这段里只要有一天填过流量 / 症状 / 日记就整体拒绝——把能删的删掉、剩下的留着，
     * 会留下一段孤立的脏数据，比改不动更糟。提示里报出是哪几天。
     */
    fun changePeriodStart(date: LocalDate) {
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            val message = periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val today = todaySource.currentDate()
                    if (date.isAfter(today)) return@withContext "开始日期不能晚于今天"
                    val previousOngoing = settingsRepository.ongoingPeriodStart.first()?.let(::parseDateOrNull)
                    val records = recordRepository.observeRecordsInRange(
                        today.minusYears(3).toString(), today.toString(), MenstruationFields.TYPE_CODE
                    ).first()
                    val recorded = records.mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
                    val derived = if (previousOngoing != null) {
                        recorded + PeriodOngoing.span(previousOngoing, today)
                    } else {
                        recorded
                    }
                    val segment = CycleCalculator.segmentsOf(derived, today).lastOrNull()
                        ?: return@withContext "还没有可修改的经期记录，先点「大姨妈来了」开始一段"
                    if (date !in DateRange(segment.start, segment.end)) {
                        return@withContext "只能把最近一段经期（${cnDate(segment.start)} – ${cnDate(segment.end)}）" +
                            "里的某一天设为开始日"
                    }
                    if (date == segment.start) {
                        return@withContext "开始日已经是 ${cnDate(date)}"
                    }

                    val locked = records.filterNot { isMarkOnly(it) }
                        .mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
                    val plan = PeriodStartEdit.plan(segment.start, date, locked)
                    if (!plan.canApply) {
                        val days = plan.blockedDates.joinToString("、") { cnDate(it) }
                        return@withContext "$days 填过内容，不能作为自动标记被撤掉。" +
                            "先把那几天逐项清空，再改开始日"
                    }

                    val byDate = records.associateBy { parseDateOrNull(it.event.localDate) }
                    plan.dropDates.forEach { day ->
                        byDate[day]?.let { recordRepository.deletePoopRecord(it.event.id) }
                    }
                    // 进行中的段要把起点一起挪，否则 syncOngoingPeriod 会按旧起点把撤掉的日子补回来。
                    if (previousOngoing != null) {
                        settingsRepository.setOngoingPeriodStart(date.toString())
                    }
                    buildString {
                        append("开始日已改为 ${cnDate(date)}")
                        if (plan.dropDates.isNotEmpty()) append("，撤掉之前 ${plan.dropDates.size} 天")
                    }
                }
            }
            todaySource.refresh()
            periodFeedbackChannel.trySend(message)
        }
    }

    /**
     * 「改结束日期」（参考 App 的经期结束滚轮）：把最近一段经期的结尾挪到用户选的那天。
     *
     * 这是「忘了点大姨妈走了、结束日一路跑到今天」的唯一出路：
     * - 新结束日晚于已记录末日 → 补写 `(旧末日..新结束日]` 里缺的日子；
     * - 新结束日早于已记录末日 → 撤掉结束日之后的**仅标记**日子（自动延续写下的那些），
     *   用户填过流量 / 症状 / 日记的一天一律保留，并在提示里报数；
     * - 进行中的经期改完即视为结束（清掉进行中状态，之后不再自动延续）。
     *
     * 向后补写沿用 [PeriodFill.MAX_GAP_DAYS] 的口径，超过就要求逐日确认，不一次性大批量写入。
     * 可「撤销上一步」——撤掉的日子会按原样补回。
     */
    fun changePeriodEnd(date: LocalDate) {
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            val message = periodWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    val today = todaySource.currentDate()
                    if (date.isAfter(today)) return@withContext "结束日期不能晚于今天"
                    val previousOngoing = settingsRepository.ongoingPeriodStart.first()?.let(::parseDateOrNull)
                    val records = recordRepository.observeRecordsInRange(
                        today.minusYears(3).toString(), today.toString(), MenstruationFields.TYPE_CODE
                    ).first()
                    val recorded = records.mapNotNullTo(mutableSetOf()) { parseDateOrNull(it.event.localDate) }
                    val derived = if (previousOngoing != null) {
                        recorded + PeriodOngoing.span(previousOngoing, today)
                    } else {
                        recorded
                    }
                    val segment = CycleCalculator.segmentsOf(derived, today).lastOrNull()
                        ?: return@withContext "还没有可修改的经期记录，先点「大姨妈来了」开始一段"
                    if (date.isBefore(segment.start)) {
                        return@withContext "结束日期不能早于本段开始日（${cnDate(segment.start)}）"
                    }
                    if (ChronoUnit.DAYS.between(segment.end, date) > PeriodFill.MAX_GAP_DAYS) {
                        return@withContext "比原结束日晚 ${ChronoUnit.DAYS.between(segment.end, date)} 天，" +
                            "超过 ${PeriodFill.MAX_GAP_DAYS} 天请逐日确认后再补齐"
                    }

                    val plan = PeriodEndEdit.plan(segment.start, segment.end, date, recorded)
                    plan.fillDates.forEach { recordRepository.addRecord(periodMarkDraft(it)) }

                    val trimSet = plan.trimDates.toHashSet()
                    val trimmable = mutableListOf<BackupRecord>()
                    var kept = 0
                    records.forEach { record ->
                        val day = parseDateOrNull(record.event.localDate) ?: return@forEach
                        if (day !in trimSet) return@forEach
                        if (isMarkOnly(record)) trimmable += record else kept++
                    }
                    trimmable.forEach { record -> recordRepository.deletePoopRecord(record.event.id) }

                    settingsRepository.setOngoingPeriodStart(null)
                    buildString {
                        append("结束日期已改为 ${cnDate(date)}")
                        if (plan.fillDates.isNotEmpty()) append("，补写 ${plan.fillDates.size} 天")
                        if (trimmable.isNotEmpty()) append("，撤掉之后 ${trimmable.size} 天")
                        if (kept > 0) {
                            append("（$kept 天填过内容已保留，进那一天逐项清空后可再改）")
                        }
                    }
                }
            }
            todaySource.refresh()
            periodFeedbackChannel.trySend(message)
        }
    }

    fun saveGenericRecord(eventId: String?, draft: RecordDraft) {
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            if (eventId == null) recordRepository.addRecord(draft)
            else recordRepository.updateRecord(eventId, draft)
            todaySource.refresh()
        }
    }

    fun deleteRecord(eventId: String) {
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            recordRepository.deletePoopRecord(eventId)
            todaySource.refresh()
        }
    }
    /** 仅标记事件：没有详情、备注也为空——只有这种由自动延续写下的日子才可以被撤掉。 */
    private fun isMarkOnly(record: BackupRecord): Boolean =
        record.detail == null && record.event.note.isBlank()

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


    class Factory(
        private val recordRepository: RecordRepository,
    private val typeCatalogRepository: TypeCatalogRepository,
        private val settingsRepository: SettingsRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(PeriodViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return PeriodViewModel(recordRepository, typeCatalogRepository, settingsRepository, todaySource) as T
        }
    }
}
