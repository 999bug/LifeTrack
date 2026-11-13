package com.example.lifetrack.feature.sport

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.home.MainViewModel
import com.example.lifetrack.feature.statistics.rememberStatsColors
import java.time.LocalDate
import java.time.ZoneId

/** 列表最多一次画 30 条，再多引导去「历史」页；避免长列表把首屏滚动深度拖到几屏之外。 */
private const val VISIBLE_LIMIT = 30

/**
 * 运动子应用首屏（界面定稿：节奏型）。
 *
 * 定位不是「第二个记录表单」——记录弹层早就有了。它补的是首页和统计页都看不到的那一层：
 * 这周动了几次、三类各占多少、断没断。所以首屏顺序是
 * 「本周概览 → 三类快记 → 最近记录 → 简报」，记录只占中间一小块。
 *
 * 所有数字都由 [SportStats] 从已有记录派生，不落库、不改备份格式；
 * 三类共用同一套字段（类型 / 时长 / 强度），差异只体现在图标、占比与筛选上。
 */
@Composable
fun SportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onRecord: (String) -> Unit,
    onShowDetail: (BackupRecord) -> Unit,
    onEdit: (BackupRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val types by viewModel.trackTypes.collectAsStateWithLifecycle()
    val records by viewModel.sportRecords.collectAsStateWithLifecycle()
    val enabled = types.any { it.code == SportFields.TYPE_CODE && it.enabled }

    val today = LocalDate.now(ZoneId.systemDefault())
    val sessions = remember(records) { SportStats.sessions(records) }
    val week = remember(sessions, today) { SportStats.week(sessions, today) }
    val brief = remember(sessions, today) { SportStats.brief(sessions, today) }
    val gap = remember(sessions, today) { SportStats.gapLabel(sessions.firstOrNull()?.date, today) }
    val recordById = remember(records) { records.associateBy { it.event.id } }

    val moduleColor = LifeTrackTokens.ModuleColors.of(SportFields.TYPE_CODE)
    val colors = rememberStatsColors()
    val stacked = LocalDensity.current.fontScale > 1.3f

    var filter by rememberSaveable { mutableStateOf("") }
    var briefOpen by rememberSaveable { mutableStateOf(false) }
    val visible = remember(sessions, filter) {
        if (filter.isEmpty()) sessions else sessions.filter { it.sport == filter }
    }

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
                text = "运动",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "近 ${SportStats.WINDOW_DAYS} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!enabled) {
            Text(
                text = "「运动」记录项目尚未启用。到「设置 → 记录项目」开启后，点首页的「运动」卡即可进入这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
            return@Column
        }

        SportWeekCard(
            week = week,
            gap = gap,
            moduleColor = moduleColor,
            surface = colors.surface,
            border = colors.border
        )

        SectionHeader(title = "记一次", hint = "选类型后填时长")
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SportFields.SPORTS.forEach { sport ->
                    SportQuickCard(
                        sport = sport,
                        last = SportStats.lastOf(sessions, sport),
                        accent = moduleColor,
                        stacked = true,
                        onClick = { onRecord(sport) }
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(LifeTrackTokens.Spacing.gap)) {
                SportFields.SPORTS.forEach { sport ->
                    SportQuickCard(
                        sport = sport,
                        last = SportStats.lastOf(sessions, sport),
                        accent = moduleColor,
                        stacked = false,
                        onClick = { onRecord(sport) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionHeader(
            title = "最近记录",
            hint = if (sessions.isEmpty()) "还没有" else "${visible.size} 条"
        )

        if (sessions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter.isEmpty(),
                    onClick = { filter = "" },
                    label = { Text("全部") }
                )
                SportFields.SPORTS.forEach { sport ->
                    FilterChip(
                        selected = filter == sport,
                        onClick = { filter = if (filter == sport) "" else sport },
                        label = { Text(SportFields.sportLabel(sport)) }
                    )
                }
            }
        }

        if (sessions.isEmpty()) {
            Text(
                text = "这段时间还没有运动记录。点上面任意一张类型卡就能记一次，记几天后这里会出现节奏与占比。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (visible.isEmpty()) {
            Text(
                text = "这个类型近 ${SportStats.WINDOW_DAYS} 天还没有记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.take(VISIBLE_LIMIT).forEach { session ->
                    val record = recordById[session.id]
                    if (record != null) {
                        SportHistoryRow(
                            session = session,
                            today = today,
                            stacked = stacked,
                            onShowDetail = { onShowDetail(record) },
                            onEdit = { onEdit(record) },
                            onDelete = { onDelete(record.event.id) }
                        )
                    }
                }
            }
            if (visible.size > VISIBLE_LIMIT) {
                Text(
                    text = "只显示最近 $VISIBLE_LIMIT 条；更早的记录在「历史」页按日期查。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SportInsightSection(expanded = briefOpen, onToggle = { briefOpen = !briefOpen }) {
            SportBriefGrid(brief = brief, stacked = stacked)
            Text(
                text = "只统计你已记录的内容：时长按分钟累加，旧类型（步行 / 游泳 / 力量 / 其他）" +
                    "仍计入次数与时长，但不单独出现在三色占比里。不做热量、配速或距离推算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "运动记录只用于回看自己的节奏，不构成训练建议；如有伤病或不适请咨询专业人士。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String, hint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
