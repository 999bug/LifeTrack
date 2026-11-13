package com.example.lifetrack.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.record.RecordSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val todayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)

/**
 * 首页（界面定稿 v2 · 批 1）：页头 + 通栏周期概览卡 + 等高模块网格 + 今日时间轴。
 *
 * 与旧版的三处结构差别，正是「每个框大小不一致」的根因：
 * 1. 网格不再每行各自决定高度：所有卡片统一 [cardHeight]，行间不再出现 28dp 落差；
 * 2. [ModuleCard] 的动作槽对所有模块常驻，同行卡片不再差 16dp；
 * 3. 月经卡移出网格升级成通栏概览卡（它是子应用入口，不是「记一条」），
 *    奇数模块时最后一张自动升为通栏宽卡，不再用 Spacer 留半行空白。
 *
 * 停用的模块不进入首页，启用入口只在「设置 → 记录项目」；未启用月经时不显示概览卡。
 *
 * 运动卡留在网格里，但整卡点击改成进入「运动」子应用，右下角动作槽保留「记一次」直接开弹层——
 * 「记录」是高频动作，「看节奏」不是，所以入口分层要反过来。
 */
@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    onQuickRecord: () -> Unit,
    onWaterQuick: () -> Unit,
    onDetailedRecord: () -> Unit,
    onWaterRecord: () -> Unit,
    onCoffeeRecord: () -> Unit,
    onSportRecord: () -> Unit,
    onOpenSport: () -> Unit,
    onIntimacyRecord: () -> Unit,
    onOpenPeriod: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowDetail: (BackupRecord) -> Unit,
    onEdit: (BackupRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.todayRecords.collectAsStateWithLifecycle()
    val trackTypes by viewModel.trackTypes.collectAsStateWithLifecycle()
    val snapshot by viewModel.cycleSnapshot.collectAsStateWithLifecycle()
    val ongoingStart by viewModel.ongoingPeriodStart.collectAsStateWithLifecycle()
    val cycleDays by viewModel.cycleLengthDays.collectAsStateWithLifecycle()
    val periodDays by viewModel.periodLengthDays.collectAsStateWithLifecycle()

    val enabledTypes = trackTypes.filter { it.enabled }
    val gridTypes = enabledTypes.filter { it.code != MenstruationFields.TYPE_CODE }
    val periodEnabled = enabledTypes.any { it.code == MenstruationFields.TYPE_CODE }
    // 首页、历史、月历与统计默认都按「全部类型」同一口径计数，这里不再只数排便。
    val countsByType = records.groupingBy { it.event.typeCode }.eachCount()
    val typeNames = remember(enabledTypes) { enabledTypes.associate { it.code to it.name } }
    val fontScale = LocalDensity.current.fontScale
    val columns = moduleColumns(fontScale)
    val cardHeight = if (fontScale > 1.3f) 176.dp else 128.dp

    val today = LocalDate.now()
    val lastTime = records.firstOrNull()?.let { RecordSummary.time(it) }
    val subtitle = buildString {
        append("已记录 ${records.size} 次")
        if (lastTime != null) append(" · 最近一次 $lastTime")
    }
    val doneCount = gridTypes.count { (countsByType[it.code] ?: 0) > 0 }

    LazyColumn(
        contentPadding = PaddingValues(
            start = LifeTrackTokens.Spacing.page,
            end = LifeTrackTokens.Spacing.page,
            top = 12.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(LifeTrackTokens.Spacing.gap)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = today.format(todayFormatter),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.4.sp
                    )
                    Text(
                        text = "今天",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (gridTypes.isNotEmpty()) {
                    Text(
                        text = "已完成 $doneCount / ${gridTypes.size}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (trackTypes.isEmpty()) {
            // 首次进入时 Room 还没吐出类型表，此时既不是「没有启用项」也不该闪一下空态，先不渲染。
        } else if (enabledTypes.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("当前没有启用的记录项目", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "在「设置 → 记录项目」里启用后就能在这里记录。已有的历史记录不受影响。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )
                    OutlinedButton(onClick = onOpenSettings) { Text("去设置") }
                }
            }
        } else {
            if (periodEnabled) {
                item {
                    CycleOverviewCard(
                        snapshot = snapshot,
                        ongoingStart = ongoingStart,
                        cycleDays = cycleDays,
                        periodDays = periodDays,
                        today = today,
                        onClick = onOpenPeriod
                    )
                }
            }

            if (gridTypes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "快速记录",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${gridTypes.size} 个项目 · 常驻动作槽",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LifeTrackTokens.Spacing.gap)) {
                        val rows = gridTypes.chunked(columns)
                        rows.forEach { rowTypes ->
                            // 奇数模块时最后一张独占一行并升为通栏宽卡，不再用 Spacer 留半行空白。
                            val wide = columns == 2 && rowTypes.size == 1 && gridTypes.size % 2 == 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(LifeTrackTokens.Spacing.gap)
                            ) {
                                rowTypes.forEach { type ->
                                    val quick = type.isQuickAction
                                    // 运动卡是子应用入口：整卡进「运动」，右下角动作槽仍直接记一次，
                                    // 高频记录路径不多点一次。
                                    val opensSubApp = type.code == SportFields.TYPE_CODE
                                    val record = moduleAction(
                                        type, onQuickRecord, onWaterQuick, onWaterRecord,
                                        onCoffeeRecord, onSportRecord, onIntimacyRecord
                                    )
                                    val detail = moduleDetail(
                                        type, onDetailedRecord, onWaterRecord,
                                        onCoffeeRecord, onSportRecord, onIntimacyRecord
                                    )
                                    ModuleCard(
                                        type = type,
                                        count = countsByType[type.code] ?: 0,
                                        summary = moduleSummary(records, type),
                                        actionLabel = moduleActionLabel(type, quick),
                                        onRecord = if (opensSubApp) onOpenSport else record,
                                        onAction = when {
                                            opensSubApp -> record
                                            quick -> detail
                                            else -> record
                                        },
                                        cardHeight = cardHeight,
                                        wide = wide,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (!wide && rowTypes.size < columns) {
                                    repeat(columns - rowTypes.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "今日记录",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${records.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (records.isEmpty()) {
            item {
                Text(
                    text = "今天还没有记录。点上面的模块卡就能记一条。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(records, key = { _, record -> record.event.id }) { index, record ->
                CompactRecordRow(
                    record = record,
                    typeNames = typeNames,
                    onShowDetail = { onShowDetail(record) },
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record.event.id) },
                    showDivider = index < records.lastIndex
                )
            }
        }
    }
}

/** 有合理默认值、可以直接落一条记录的模块。 */
private val TrackTypeEntity.isQuickAction: Boolean
    get() = code == DEFAULT_POOP_TYPE.code || code == WaterFields.TYPE_CODE

/** 动作槽文案：快捷记录型保留「详细」入口，其余直接写清楚这一下会记什么。 */
private fun moduleActionLabel(type: TrackTypeEntity, quickAction: Boolean): String = when {
    quickAction -> "详细"
    type.code == CoffeeFields.TYPE_CODE -> "记一杯"
    type.code == SportFields.TYPE_CODE -> "记一次"
    type.code == IntimacyFields.TYPE_CODE -> "记一次"
    else -> "记一条"
}

private fun moduleAction(
    type: TrackTypeEntity,
    onQuickRecord: () -> Unit,
    onWaterQuick: () -> Unit,
    onWaterRecord: () -> Unit,
    onCoffeeRecord: () -> Unit,
    onSportRecord: () -> Unit,
    onIntimacyRecord: () -> Unit
): () -> Unit = when (type.code) {
    DEFAULT_POOP_TYPE.code -> onQuickRecord
    WaterFields.TYPE_CODE -> onWaterQuick
    CoffeeFields.TYPE_CODE -> onCoffeeRecord
    SportFields.TYPE_CODE -> onSportRecord
    IntimacyFields.TYPE_CODE -> onIntimacyRecord
    else -> onWaterRecord
}

private fun moduleDetail(
    type: TrackTypeEntity,
    onDetailedRecord: () -> Unit,
    onWaterRecord: () -> Unit,
    onCoffeeRecord: () -> Unit,
    onSportRecord: () -> Unit,
    onIntimacyRecord: () -> Unit
): () -> Unit = when (type.code) {
    DEFAULT_POOP_TYPE.code -> onDetailedRecord
    WaterFields.TYPE_CODE -> onWaterRecord
    CoffeeFields.TYPE_CODE -> onCoffeeRecord
    SportFields.TYPE_CODE -> onSportRecord
    IntimacyFields.TYPE_CODE -> onIntimacyRecord
    // 未知类型没有编辑器可用；「详细」只在快捷记录型卡片上出现，不会走到这里
    else -> onWaterRecord
}

/**
 * 模块卡状态行里的份量摘要。
 *
 * 只汇总 ml 口径：用户把单位切成「杯」时不做换算，宁可少显示一个数字，
 * 也不给出一个凭猜测折算出来的总量。
 */
private fun moduleSummary(records: List<BackupRecord>, type: TrackTypeEntity): String? {
    val amountKey = when (type.code) {
        WaterFields.TYPE_CODE -> WaterFields.AMOUNT
        CoffeeFields.TYPE_CODE -> CoffeeFields.AMOUNT
        else -> return null
    }
    var total = 0
    records.forEach { record ->
        if (record.event.typeCode != type.code) return@forEach
        val fields = (record.detail as? RecordDetail.Generic)?.fields ?: return@forEach
        val unit = fields[WaterFields.UNIT] ?: "ml"
        if (unit != "ml") return@forEach
        total += fields[amountKey]?.toIntOrNull() ?: 0
    }
    return if (total > 0) "$total ml" else null
}
