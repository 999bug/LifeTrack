package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CycleCalculator
import com.example.lifetrack.core.model.CycleSnapshot
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PeriodDayFormModel
import com.example.lifetrack.core.model.PeriodFill
import com.example.lifetrack.core.model.PeriodMonth
import com.example.lifetrack.core.model.PeriodOngoing
import com.example.lifetrack.core.model.Phase
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.intimacy.IntimacyDaySection
import com.example.lifetrack.feature.intimacy.IntimacyEditorDialog
import com.example.lifetrack.feature.statistics.CardShape
import com.example.lifetrack.feature.statistics.rememberStatsColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 月历翻页范围：过去 3 年（与 periodRecords 取数区间一致）到未来 6 个月（覆盖 3 个预测窗口）。 */
private const val MIN_MONTH_OFFSET = -36
private const val MAX_MONTH_OFFSET = 6

/**
 * 经期子应用首屏（界面定稿 v2 · 批 2）。
 *
 * 从「日历 + 表单」升级为「看得见的周期」：92dp 周期环 → 28 段连续色带 → 日历 →
 * 单一 62dp 主行动（未开始 / 经期中 / 已结束三态）→ 逐行填写 → 周期洞察折叠。
 * 所有派生数字来自 [MainViewModel.cycleSnapshot]，不落库、不产出健康结论。
 *
 * 旧版「大姨妈来了」在经期中被设成 disabled 灰块，FlowRow 里两键错位；
 * 现在只剩一个主按钮，撤销与改开始日期作为次级动作，任何时候布局都对齐。
 */
@Composable
fun PeriodScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val types by viewModel.trackTypes.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val enabled = types.any { it.code == MenstruationFields.TYPE_CODE && it.enabled }
    val periodRecords by viewModel.periodRecords.collectAsStateWithLifecycle()
    val snapshot by viewModel.cycleSnapshot.collectAsStateWithLifecycle()
    val ongoingStart by viewModel.ongoingPeriodStart.collectAsStateWithLifecycle()
    val cycleDays by viewModel.cycleLengthDays.collectAsStateWithLifecycle()
    val periodDays by viewModel.periodLengthDays.collectAsStateWithLifecycle()

    val moduleColor = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val colors = rememberStatsColors()
    val stacked = LocalDensity.current.fontScale > 1.3f

    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedIso by rememberSaveable { mutableStateOf(today.toString()) }
    var insightOpen by rememberSaveable { mutableStateOf(false) }
    var endPickerOpen by rememberSaveable { mutableStateOf(false) }
    val selectedDate = remember(selectedIso) { LocalDate.parse(selectedIso) }
    val month = remember(today, monthOffset) { PeriodMonth.of(today, monthOffset) }

    // 已落库的经期日 + 进行中区间 [起点..今天]：后者还没写进库，但日历与状态条要一起显示。
    val recordedDates = remember(periodRecords, ongoingStart, today) {
        val start = ongoingStart
        buildSet {
            periodRecords.mapNotNullTo(this) {
                runCatching { LocalDate.parse(it.event.localDate) }.getOrNull()
            }
            if (start != null) addAll(PeriodOngoing.span(start, today))
        }
    }
    // 最近一段经期：起止修改只作用于它，避免一次误触动到几个月前的历史。
    val lastSegment = remember(recordedDates, today) {
        CycleCalculator.segmentsOf(recordedDates, today).lastOrNull()
    }
    val lastSegmentDays = remember(lastSegment) {
        lastSegment?.let { PeriodOngoing.span(it.start, it.end).toSet() }.orEmpty()
    }
    val selectedInSegment = lastSegment != null && selectedDate in lastSegmentDays
    val predictedDates = remember(snapshot) {
        snapshot.predictedPeriods.flatMap { range ->
            (0 until range.lengthDays).map { range.start.plusDays(it.toLong()) }
        }.toSet()
    }
    val recordForDate = periodRecords.firstOrNull { it.event.localDate == selectedIso }

    // 亲密关系：与月经同源但独立成事件（一天可以多次），只取选中日的记录 + 最近一次用过的措施。
    val intimacyRecords by viewModel.intimacyRecords.collectAsStateWithLifecycle()
    val intimacyEnabled = types.any { it.code == IntimacyFields.TYPE_CODE && it.enabled }
    val dayIntimacy = remember(intimacyRecords, selectedIso) {
        intimacyRecords
            .filter { it.event.localDate == selectedIso }
            .sortedBy { it.event.occurredAtEpochMillis }
    }
    // 「上次」= 除当前正在编辑的那条以外最近一次的措施；同一天更早的记录也算，不按天切。
    fun lastIntimacyMethod(excludeId: String?): String? = intimacyRecords
        .filter { it.event.id != excludeId }
        .maxByOrNull { it.event.occurredAtEpochMillis }
        ?.let { (it.detail as? RecordDetail.Generic)?.fields?.get(IntimacyFields.METHOD) }

    var intimacyTarget by remember { mutableStateOf<IntimacyTarget?>(null) }
    var intimacyPendingDelete by remember { mutableStateOf<BackupRecord?>(null) }

    fun openIntimacyEditor(record: BackupRecord?, ordinal: Int) {
        intimacyTarget = IntimacyTarget(record, ordinal)
    }

    // 表单状态：切换选中日时从当天记录重建；用户一旦交互就不再被外部数据流覆盖。
    var formModel by remember(selectedDate) {
        mutableStateOf(PeriodDayFormModel.of(today, selectedDate, recordForDate))
    }
    var formEdited by remember(selectedDate) { mutableStateOf(false) }
    LaunchedEffect(selectedDate, recordForDate) {
        if (!formEdited) {
            formModel = PeriodDayFormModel.of(today, selectedDate, recordForDate)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.periodFeedback.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun persist(model: PeriodDayFormModel) {
        viewModel.savePeriodDay(selectedDate, model.toFields(), model.toNote())
    }

    val canEdit = !selectedDate.isAfter(today)
    val canGoNext = monthOffset < MAX_MONTH_OFFSET
    val actionDate = if (selectedDate.isAfter(today)) today else selectedDate

    // 周期环 / 色带的口径：优先用样本中位数，样本不足时回退到用户配置值。
    val cycleLength = snapshot.medianCycleDays ?: cycleDays
    val periodLength = snapshot.medianPeriodDays ?: periodDays
    val cycleStart = ongoingStart ?: snapshot.segments.lastOrNull()?.start
    val dayIndex = cycleDayIndex(cycleStart, today, cycleLength)
    val recordedDays = when {
        ongoingStart != null -> ChronoUnit.DAYS.between(ongoingStart, today).toInt() + 1
        else -> snapshot.segments.lastOrNull()?.lengthDays ?: 0
    }.coerceAtLeast(0)

    val nextStart = snapshot.predictedPeriods.firstOrNull()?.start
    val phaseText = periodPhaseText(
        snapshot = snapshot,
        ongoingStart = ongoingStart,
        today = today,
        cycleLength = cycleLength,
        nextStart = nextStart
    )

    // 「改结束日期」的可选范围：最近一段的首日 .. 今天。已结束的段只额外放宽
    // PeriodFill.MAX_GAP_DAYS 天，免得一次误触把一段很老的经期一路补到今天。
    val endEditFirst = lastSegment?.start
    val endEditInitial = lastSegment?.end ?: today
    val endEditLast = if (ongoingStart != null) {
        today
    } else {
        minOf(today, endEditInitial.plusDays(PeriodFill.MAX_GAP_DAYS.toLong()))
    }
    val canSetStart = selectedInSegment && selectedDate != lastSegment.start

    // 单一主行动状态机：未开始 → 经期中 → 已结束（有历史记录但无进行中区间）。
    val primaryLabel: String
    val primaryIsCheck: Boolean
    val primaryAction: () -> Unit
    when {
        ongoingStart != null -> {
            primaryLabel = "大姨妈走了"
            primaryIsCheck = true
            primaryAction = { viewModel.markPeriodEnd(actionDate) }
        }

        snapshot.segments.isNotEmpty() -> {
            primaryLabel = "开始下一次经期"
            primaryIsCheck = false
            primaryAction = { viewModel.markPeriodStart(actionDate) }
        }

        else -> {
            primaryLabel = "大姨妈来了"
            primaryIsCheck = false
            primaryAction = { viewModel.markPeriodStart(actionDate) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PeriodPageHeader(
                onBack = onBack,
                settingsEnabled = enabled,
                onOpenSettings = { insightOpen = true }
            )

            if (!enabled) {
                Text(
                    text = "「月经」记录项目尚未启用。到「设置 → 记录项目」开启后，点首页的「月经」卡即可进入这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
                // 只开了亲密关系时，本页仍要可用：给出今天这一段，不做周期推算。
                if (intimacyEnabled) {
                    Text(
                        text = "今天 · 亲密关系",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    IntimacyDaySection(
                        records = dayIntimacy,
                        readOnly = false,
                        moduleColor = LifeTrackTokens.ModuleColors.of(IntimacyFields.TYPE_CODE),
                        onAdd = { openIntimacyEditor(null, dayIntimacy.size + 1) },
                        onEdit = { record -> openIntimacyEditor(record, dayIntimacy.indexOf(record) + 1) },
                        onDelete = { intimacyPendingDelete = it }
                    )
                }
                return@Column
            }

            PeriodPhaseCard(
                dayIndex = dayIndex,
                cycleLength = cycleLength,
                periodLength = periodLength,
                recordedDays = recordedDays,
                badge = phaseText.first,
                title = phaseText.second,
                description = phaseText.third,
                summary = "${phaseText.first}，${phaseText.second}。${phaseText.third} " +
                    "周期第 $dayIndex 天，共 $cycleLength 天。"
            )

            PeriodCycleBandCard(
                dayIndex = dayIndex,
                cycleLength = cycleLength,
                periodLength = periodLength,
                recordedDays = recordedDays,
                startDate = cycleStart
            )

            PeriodCalendarView(
                month = month,
                today = today,
                recordedDates = recordedDates,
                predictedDates = predictedDates,
                selectedDate = selectedDate,
                canGoNext = canGoNext,
                onPrevious = { monthOffset = (monthOffset - 1).coerceAtLeast(MIN_MONTH_OFFSET) },
                onNext = { monthOffset = (monthOffset + 1).coerceAtMost(MAX_MONTH_OFFSET) },
                onToday = {
                    monthOffset = 0
                    selectedIso = today.toString()
                },
                onSelectDate = { date -> selectedIso = date.toString() }
            )

            if (canEdit) {
                val segmentHint = lastSegment?.let {
                    "最近一段：${cn(it.start)} – ${cn(it.end)} · 共 ${it.lengthDays} 天"
                }
                PeriodPrimaryActions(
                    primaryLabel = primaryLabel,
                    primaryIsCheck = primaryIsCheck,
                    onPrimary = primaryAction,
                    showSecondary = selectedInSegment,
                    canSetStart = canSetStart,
                    onSetStart = { viewModel.changePeriodStart(selectedDate) },
                    canEditEnd = selectedInSegment,
                    onEditEnd = { endPickerOpen = true },
                    hint = when {
                        selectedInSegment -> buildString {
                            append(segmentHint)
                            if (selectedDate == lastSegment.start) {
                                append("；这一天已经是开始日")
                            }
                        }
                        lastSegment != null ->
                            "$segmentHint。把某天设为开始日、或调整结束日：先点这段里的某一天。"
                        else -> null
                    }
                )
                // 忘了点「大姨妈走了」是这里最常见的状态：连日数明显超过平常时，直接给出改法。
                if (ongoingStart != null && recordedDays > periodLength) {
                    Text(
                        text = "本次已连记 $recordedDays 天，比你通常的 $periodLength 天更长。" +
                            "如果早就结束了，点日历上这段里的任意一天，再点「改结束日期」把结束日调回去。",
                        style = MaterialTheme.typography.bodySmall,
                        color = moduleColor
                    )
                }
                // 改结束日期用滚轮弹层（参考 App 的「选择经期结束日期」）：结束日能被事后改回来，
                // 不再依赖「当天记得点大姨妈走了」。
                val editFirst = endEditFirst
                if (endPickerOpen && editFirst != null) {
                    PeriodEndDatePicker(
                        earliest = editFirst,
                        latest = endEditLast,
                        initialEnd = endEditInitial,
                        today = today,
                        onDismiss = { endPickerOpen = false },
                        onConfirm = { picked ->
                            endPickerOpen = false
                            viewModel.changePeriodEnd(picked)
                        }
                    )
                }
            } else {
                Text(
                    text = "未来日期不能开始或结束经期。点「回到今天」或选一个过去的日子。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SelectedDayHeader(
                selectedDate = selectedDate,
                today = today,
                canEdit = canEdit,
                accent = moduleColor,
                filledCount = com.example.lifetrack.core.model.PeriodField.entries.count {
                    formModel.isFilled(it)
                }
            )

            // 亲密关系在「流量」一栏上方：它是独立事件，卡片自带记录列表与新增入口。
            if (intimacyEnabled) {
                IntimacyDaySection(
                    records = dayIntimacy,
                    readOnly = !canEdit,
                    moduleColor = LifeTrackTokens.ModuleColors.of(IntimacyFields.TYPE_CODE),
                    onAdd = { openIntimacyEditor(null, dayIntimacy.size + 1) },
                    onEdit = { record -> openIntimacyEditor(record, dayIntimacy.indexOf(record) + 1) },
                    onDelete = { intimacyPendingDelete = it }
                )
            }

            PeriodDayForm(
                model = formModel,
                predicted = selectedDate in predictedDates,
                onToggleRow = { field ->
                    formEdited = true
                    formModel = formModel.toggleRow(field)
                },
                onSelectOption = { field, value ->
                    formEdited = true
                    val next = formModel.selectOption(field, value)
                    formModel = next
                    persist(next)
                },
                onToggleSymptom = { code ->
                    formEdited = true
                    val next = formModel.toggleSymptom(code)
                    formModel = next
                    persist(next)
                },
                onDiaryChange = { text ->
                    formEdited = true
                    val next = formModel.setDiary(text)
                    formModel = next
                    persist(next)
                },
                onDone = {
                    formEdited = true
                    val next = formModel.closeRow()
                    formModel = next
                    persist(next)
                    scope.launch { snackbarHostState.showSnackbar("已保存当天记录") }
                }
            )

            PeriodInsightSection(expanded = insightOpen, onToggle = { insightOpen = !insightOpen }) {
                Text("本周期简报", style = MaterialTheme.typography.titleSmall)
                PeriodBriefCard(
                    snapshot = snapshot,
                    recordedDays = recordedDates.size,
                    records = periodRecords,
                    stacked = stacked,
                    surface = colors.surface,
                    border = colors.border
                )
                Text("周期长度趋势", style = MaterialTheme.typography.titleSmall)
                PeriodTrendCard(
                    snapshot = snapshot,
                    moduleColor = moduleColor,
                    surface = colors.surface,
                    border = colors.border
                )
                PeriodConfigSection(
                    cycleDays = cycleDays,
                    periodDays = periodDays,
                    onCycleChange = viewModel::setCycleLengthDays,
                    onPeriodChange = viewModel::setPeriodLengthDays,
                    surface = colors.surface,
                    border = colors.border
                )
                // 旧版「未开始 → 大姨妈走了」的补齐能力保留在洞察里，不占首屏主行动位。
                if (ongoingStart == null) {
                    OutlinedButton(
                        onClick = { viewModel.markPeriodEnd(today) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) { Text("补齐最近一段经期") }
                }
            }

            Text(
                text = "周期与预计日期只按你已记录的日期推算，仅供记录参考，不用于避孕或医疗判断；" +
                    "如有异常请及时就医。未记录的日子不会计入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    intimacyTarget?.let { target ->
        IntimacyEditorDialog(
            record = target.record,
            ordinal = target.ordinal,
            defaultDate = selectedDate,
            lastMethod = lastIntimacyMethod(target.record?.event?.id),
            onDismiss = { intimacyTarget = null },
            onSave = { draft ->
                viewModel.saveGenericRecord(target.record?.event?.id, draft)
                intimacyTarget = null
            }
        )
    }

    intimacyPendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { intimacyPendingDelete = null },
            title = { Text("删除这条亲密记录？") },
            text = { Text("删除后无法在应用内撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(record.event.id)
                    intimacyPendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { intimacyPendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 亲密关系编辑目标：null 记录 = 新增（[ordinal] 用于「第 N 次」标题）。 */
private data class IntimacyTarget(
    val record: BackupRecord?,
    val ordinal: Int
)

/** 阶段文案：只描述已记录的事实与推算日期，不给健康结论或调理建议。 */
private fun periodPhaseText(
    snapshot: CycleSnapshot,
    ongoingStart: LocalDate?,
    today: LocalDate,
    cycleLength: Int,
    nextStart: LocalDate?
): Triple<String, String, String> {
    if (ongoingStart != null) {
        val day = ChronoUnit.DAYS.between(ongoingStart, today).toInt() + 1
        return Triple(
            "当前阶段 · 经期中",
            "月经期 · 第 $day 天",
            "自 ${cn(ongoingStart)} 起已记录 $day 天；结束时点下方按钮，中间缺的日子会自动补齐。"
        )
    }
    return when (val phase = snapshot.currentPhase) {
        is Phase.InPeriod -> Triple(
            "当前阶段 · 经期中",
            "经期第 ${phase.dayIndex} 天",
            "按已记录的日期推算，今天是本段经期的第 ${phase.dayIndex} 天。"
        )

        is Phase.BeforeNext -> Triple(
            "当前阶段 · 等待下次",
            "距预计下次经期 ${phase.days} 天",
            if (nextStart != null) {
                "下次预计 ${cn(nextStart)} 开始 · 按 $cycleLength 天周期推算。"
            } else {
                "记录足够后会自动给出预计日期 · 周期按 $cycleLength 天显示。"
            }
        )

        is Phase.Delayed -> Triple(
            "当前阶段 · 已推迟",
            "较预计日期推迟 ${phase.days} 天",
            "预测只按已记录日期推算，推迟不一定代表异常；未记录的日子不会计入。"
        )

        Phase.Insufficient -> if (snapshot.segments.isNotEmpty()) {
            Triple(
                "当前阶段 · 本次已结束",
                "本次经期已结束",
                "已有 ${snapshot.segments.size} 段记录；下一次可点下方按钮开始。"
            )
        } else {
            Triple(
                "当前阶段 · 记录暂不足",
                "继续记录后可看到周期",
                "点「大姨妈来了」开始记录，之后每天自动标记。"
            )
        }
    }
}

@Composable
private fun PeriodPageHeader(
    onBack: () -> Unit,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val accent = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val stacked = LocalDensity.current.fontScale > 1.3f
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回今天")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("经期", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "关注周期，不判断健康",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PeriodHeaderMark(accent)
            }
            if (settingsEnabled) {
                TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("周期设置")
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回今天")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("经期", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "关注周期，不判断健康",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PeriodHeaderMark(accent)
            if (settingsEnabled) {
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("设置")
                }
            }
        }
    }
}

@Composable
private fun PeriodHeaderMark(accent: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(accent.copy(alpha = 0.11f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Bloodtype,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SelectedDayHeader(
    selectedDate: LocalDate,
    today: LocalDate,
    canEdit: Boolean,
    accent: Color,
    filledCount: Int
) {
    val weekday = selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    val title = "${cn(selectedDate)} · $weekday${if (selectedDate == today) " · 今天" else ""}"
    val count = "已填 $filledCount / ${com.example.lifetrack.core.model.PeriodField.entries.size}"
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectedDayBadge(count, accent)
                if (!canEdit) ReadOnlyBadge()
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            SelectedDayBadge(count, accent)
            if (!canEdit) ReadOnlyBadge()
        }
    }
}

@Composable
private fun SelectedDayBadge(text: String, accent: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun ReadOnlyBadge() {
    Text(
        text = "只读",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 简报只呈现用户自己记录的数据：中位数 / 最长最短 / 已记录天数 / 常见症状 Top 3，不做健康判断。 */
@Composable
private fun PeriodBriefCard(
    snapshot: CycleSnapshot,
    recordedDays: Int,
    records: List<BackupRecord>,
    stacked: Boolean,
    surface: Color,
    border: Color
) {
    val validCycles = snapshot.cycleLengths.filter { it in CycleCalculator.CYCLE_RANGE }
    val longest = validCycles.maxOrNull()
    val shortest = validCycles.minOrNull()
    val topSymptoms = remember(records) { symptomFrequency(records).take(3) }
    val symptomSummary = if (topSymptoms.isEmpty()) {
        "—"
    } else {
        topSymptoms.joinToString(" · ") { "${MenstruationFields.symptomLabel(it.first)} ${it.second} 次" }
    }
    val metrics = listOf(
        "${snapshot.medianCycleDays ?: "—"} 天" to "最近 ${CycleCalculator.DEFAULT_SAMPLE_SIZE} 个周期中位数",
        "${longest ?: "—"} / ${shortest ?: "—"} 天" to "最长 / 最短周期",
        "$recordedDays 天" to "累计已记录经期日",
        symptomSummary to "常见症状（填写天数）"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(surface)
            .border(1.dp, border, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stacked) {
            metrics.forEach { (value, label) -> BriefMetric(value, label, Modifier.fillMaxWidth()) }
        } else {
            metrics.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (value, label) -> BriefMetric(value, label, Modifier.weight(1f)) }
                }
            }
        }
        Text(
            text = "只统计已填写的内容，不推算健康结论，也不给调理建议。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BriefMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 周期长度趋势：最近 6 个周期的等宽数字 + 柱条，柱高按本组最大值归一。 */
@Composable
private fun PeriodTrendCard(
    snapshot: CycleSnapshot,
    moduleColor: Color,
    surface: Color,
    border: Color
) {
    val trend = remember(snapshot) { snapshot.segments.zip(snapshot.cycleLengths).takeLast(6) }
    val maxLen = (trend.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(surface)
            .border(1.dp, border, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "最近 ${trend.size} 个周期 · 中位数 ${snapshot.medianCycleDays ?: "—"} 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (trend.isEmpty()) {
            Text(
                text = "记录满 2 段经期后，这里会显示周期长度趋势。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                trend.forEach { (segment, length) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = length.toString(), style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .widthIn(min = 14.dp)
                                .fillMaxWidth(0.6f)
                                .height(12.dp + (length.toFloat() / maxLen * 40f).dp)
                                .clip(CardShape)
                                .background(moduleColor.copy(alpha = 0.75f))
                        )
                        Text(
                            text = "${segment.start.monthValue}月",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 周期长度与经期长度的两个步进器，收在洞察折叠里，不单独在首屏开一层。 */
@Composable
private fun PeriodConfigSection(
    cycleDays: Int,
    periodDays: Int,
    onCycleChange: (Int) -> Unit,
    onPeriodChange: (Int) -> Unit,
    surface: Color,
    border: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(surface)
            .border(1.dp, border, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StepperRow(
            label = "周期长度",
            value = cycleDays,
            range = CycleCalculator.CYCLE_RANGE,
            onChange = onCycleChange
        )
        StepperRow(
            label = "经期长度",
            value = periodDays,
            range = SettingsRepository.PERIOD_LENGTH_RANGE,
            onChange = onPeriodChange
        )
        Text(
            text = "样本不足时按设定值估算；记录足够后自动改用最近周期中位数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "减少$label")
        }
        Text(
            text = "$value 天",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 56.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "增加$label")
        }
    }
}

private fun symptomFrequency(records: List<BackupRecord>): List<Pair<String, Int>> {
    val counts = mutableMapOf<String, Int>()
    records.forEach { record ->
        val fields = (record.detail as? RecordDetail.Generic)?.fields ?: return@forEach
        MenstruationFields.decodeSymptoms(fields[MenstruationFields.SYMPTOMS].orEmpty()).forEach { code ->
            counts[code] = (counts[code] ?: 0) + 1
        }
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

private fun cn(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

/**
 * 模块色实心控件（按钮 / 日历格）的前景色。
 * 深色主题下模块色本身是浅色，用白字会糊成一片，所以按亮度切换到深色文字。
 */
internal fun onModuleColor(moduleColor: Color): Color =
    if (moduleColor.luminance() > 0.5f) Color(0xFF171A16) else Color.White
