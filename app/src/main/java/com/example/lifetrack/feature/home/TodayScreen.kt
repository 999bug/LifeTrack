package com.example.lifetrack.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
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
 * 首页：页头 + 模块网格 + 今日时间轴。
 *
 * 与旧版的两处关键差别：
 * 1. 顶部那张「今日记录 N 次」的大卡被去掉——它的信息已经写在每个模块卡的状态行里，
 *    以及页头副标题的「已记录 N 次 · 最近一次 HH:mm」，留着就是三处重复。
 * 2. 一排等宽的「记录饮水」按钮改成双列模块卡：状态与入口合并，整卡可点。
 *
 * 停用的模块不进入首页（2026-11-11 确认），也不做灰显占位；
 * 启用入口只在「设置 → 记录项目」。
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
    onIntimacyRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowDetail: (BackupRecord) -> Unit,
    onEdit: (BackupRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.todayRecords.collectAsStateWithLifecycle()
    val trackTypes by viewModel.trackTypes.collectAsStateWithLifecycle()
    val enabledTypes = trackTypes.filter { it.enabled }
    // 首页、历史、月历与统计默认都按「全部类型」同一口径计数，这里不再只数排便。
    val countsByType = records.groupingBy { it.event.typeCode }.eachCount()
    val typeNames = remember(enabledTypes) { enabledTypes.associate { it.code to it.name } }
    val columns = moduleColumns(LocalDensity.current.fontScale)

    val today = LocalDate.now().format(todayFormatter)
    val lastTime = records.firstOrNull()?.let { RecordSummary.time(it) }
    val subtitle = buildString {
        append("已记录 ${records.size} 次")
        if (lastTime != null) append(" · 最近一次 $lastTime")
    }

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
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = today,
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
            item {
                Column {
                    enabledTypes.chunked(columns).forEach { rowTypes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(LifeTrackTokens.Spacing.gap)
                        ) {
                            rowTypes.forEach { type ->
                                ModuleCard(
                                    type = type,
                                    count = countsByType[type.code] ?: 0,
                                    summary = moduleSummary(records, type),
                                    quickAction = type.isQuickAction,
                                    onRecord = { moduleAction(type, onQuickRecord, onWaterQuick, onWaterRecord, onCoffeeRecord, onSportRecord, onIntimacyRecord)() },
                                    onDetail = { moduleDetail(type, onDetailedRecord, onWaterRecord, onCoffeeRecord, onSportRecord, onIntimacyRecord)() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 最后一排只有一张卡时补一个空位，避免它被拉伸成整行宽
                            if (rowTypes.size < columns) {
                                repeat(columns - rowTypes.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(LifeTrackTokens.Spacing.gap))
                    }
                }
            }
        }

        item {
            Text(
                text = "今日记录",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
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
