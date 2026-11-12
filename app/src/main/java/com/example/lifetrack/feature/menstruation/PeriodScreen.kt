package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CycleCalculator
import com.example.lifetrack.core.model.CycleSnapshot
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PeriodDayFormModel
import com.example.lifetrack.core.model.PeriodField
import com.example.lifetrack.core.model.PeriodMonth
import com.example.lifetrack.core.model.Phase
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.statistics.CardShape
import com.example.lifetrack.feature.statistics.rememberStatsColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 月历翻页范围：过去 3 年（与 periodRecords 取数区间一致）到未来 6 个月（覆盖 3 个预测窗口）。 */
private const val MIN_MONTH_OFFSET = -36
private const val MAX_MONTH_OFFSET = 6

/**
 * 经期子应用首屏（设计 §5.4 ②）。从首页「月经」模块卡进入，隐藏底部导航。
 * 自上而下：精简状态条 → 置顶日历 → 选中日标题与起止动作 → 逐行填写区 → 简报 / 趋势 / 周期设置 / 免责。
 * 第一屏即可同时看到日历与填写区开头；所有派生数据来自 [MainViewModel.cycleSnapshot]，不落库。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val types by viewModel.trackTypes.collectAsStateWithLifecycle()
    val enabled = types.any { it.code == MenstruationFields.TYPE_CODE && it.enabled }
    val periodRecords by viewModel.periodRecords.collectAsStateWithLifecycle()
    val snapshot by viewModel.cycleSnapshot.collectAsStateWithLifecycle()
    val cycleDays by viewModel.cycleLengthDays.collectAsStateWithLifecycle()
    val periodDays by viewModel.periodLengthDays.collectAsStateWithLifecycle()

    val today = LocalDate.now(ZoneId.systemDefault())
    val moduleColor = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val colors = rememberStatsColors()
    val stacked = LocalDensity.current.fontScale > 1.3f

    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedIso by rememberSaveable { mutableStateOf(today.toString()) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val selectedDate = remember(selectedIso) { LocalDate.parse(selectedIso) }
    val month = remember(today, monthOffset) { PeriodMonth.of(today, monthOffset) }

    val recordedDates = remember(periodRecords) {
        periodRecords.mapNotNullTo(mutableSetOf()) {
            runCatching { LocalDate.parse(it.event.localDate) }.getOrNull()
        }
    }
    val predictedDates = remember(snapshot) {
        snapshot.predictedPeriods.flatMap { range ->
            (0 until range.lengthDays).map { range.start.plusDays(it.toLong()) }
        }.toSet()
    }
    val recordForDate = periodRecords.firstOrNull { it.event.localDate == selectedIso }

    // 表单状态：切换选中日时从当天记录重建；用户一旦交互就不再被外部数据流覆盖，
    // 避免即点即存 / 日记逐字输入时把乐观更新冲掉。初始加载与起止补齐属于「未交互」，会正常回填。
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回今天")
                }
                Text(
                    text = "经期",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                if (enabled) {
                    TextButton(onClick = { settingsOpen = !settingsOpen }) {
                        Text(if (settingsOpen) "收起设置" else "周期设置")
                    }
                }
            }

            if (!enabled) {
                Text(
                    text = "「月经」记录项目尚未启用。到「设置 → 记录项目」开启后，点首页的「月经」卡即可进入这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
                return@Column
            }

            PeriodStatusBar(snapshot = snapshot, today = today, moduleColor = moduleColor)

            PeriodCalendarView(
                month = month,
                today = today,
                recordedDates = recordedDates,
                predictedDates = predictedDates,
                selectedDate = selectedDate,
                canGoNext = canGoNext,
                onPrevious = { monthOffset = (monthOffset - 1).coerceAtLeast(MIN_MONTH_OFFSET) },
                onNext = { monthOffset = (monthOffset + 1).coerceAtMost(MAX_MONTH_OFFSET) },
                onSelectDate = { selectedIso = it.toString() }
            )

            SelectedDayHeader(selectedDate = selectedDate, today = today, canEdit = canEdit)

            if (canEdit) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { viewModel.markPeriodStart(selectedDate) }) {
                        Icon(
                            imageVector = LifeTrackTokens.ModuleIcons.of(MenstruationFields.TYPE_CODE),
                            contentDescription = null,
                            tint = moduleColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("大姨妈来了")
                    }
                    OutlinedButton(onClick = { viewModel.markPeriodEnd(selectedDate) }) {
                        PeriodCheck(color = moduleColor, size = 18.dp)
                        Spacer(Modifier.size(6.dp))
                        Text("大姨妈走了")
                    }
                }
                Text(
                    text = "起止动作补齐中间日期，已有记录不覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            SectionTitle("本周期简报")
            PeriodBriefCard(
                snapshot = snapshot,
                recordedDays = recordedDates.size,
                records = periodRecords,
                stacked = stacked,
                surface = colors.surface,
                border = colors.border
            )

            SectionTitle("周期长度趋势")
            PeriodTrendCard(
                snapshot = snapshot,
                moduleColor = moduleColor,
                surface = colors.surface,
                border = colors.border
            )

            if (settingsOpen) {
                PeriodConfigSection(
                    cycleDays = cycleDays,
                    periodDays = periodDays,
                    onCycleChange = viewModel::setCycleLengthDays,
                    onPeriodChange = viewModel::setPeriodLengthDays,
                    surface = colors.surface,
                    border = colors.border
                )
            }

            Text(
                text = "仅供记录参考，不用于避孕或医疗判断。预测基于已记录的日期推算，未记录的日子不会计入。",
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** 精简状态条：阶段主句 + 本期区间 / 下次预计；样本不足时降级、隐藏预测数字（设计 §5.4 ②）。 */
@Composable
private fun PeriodStatusBar(
    snapshot: CycleSnapshot,
    today: LocalDate,
    moduleColor: Color
) {
    val headline = when (val phase = snapshot.currentPhase) {
        is Phase.InPeriod -> "经期第 ${phase.dayIndex} 天"
        is Phase.BeforeNext -> "距预计下次经期 ${phase.days} 天"
        is Phase.Delayed -> "已推迟 ${phase.days} 天"
        Phase.Insufficient -> "记录暂不足，继续记录后可预测"
    }
    val badge = if (snapshot.sampleReliable) {
        "按最近 ${CycleCalculator.DEFAULT_SAMPLE_SIZE} 个周期估算"
    } else {
        "按设定值估算"
    }
    val lastStart = snapshot.segments.lastOrNull()?.start
    val nextStart = snapshot.predictedPeriods.firstOrNull()?.start
    val meta = if (snapshot.sampleReliable && lastStart != null) {
        val dayIndex = ChronoUnit.DAYS.between(lastStart, today).toInt() + 1
        buildString {
            append("本期 ${cn(lastStart)} 起 · 第 $dayIndex 天")
            if (nextStart != null) append(" · 下次预计 ${cn(nextStart)}")
        }
    } else {
        "目前只有 ${snapshot.segments.size} 段记录，还不能推算周期。继续记录几个周期后会自动给出预计日期。"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = badge, style = MaterialTheme.typography.labelMedium, color = moduleColor)
        Text(text = headline, style = MaterialTheme.typography.titleMedium)
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedDayHeader(selectedDate: LocalDate, today: LocalDate, canEdit: Boolean) {
    val weekday = selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${cn(selectedDate)} · $weekday${if (selectedDate == today) " · 今天" else ""}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (canEdit) "逐行填写当天记录" else "只读",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

/** P9 两键收在本页折叠行，不进设置页新建卡片（设计 §5.4 ⑤）。 */
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