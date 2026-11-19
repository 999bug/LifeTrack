@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.lifetrack.feature.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.feature.home.MainViewModel

/** 列表最多一次画 30 条，再多引导去「历史」页；避免长列表把首屏滚动深度拖到几屏之外。 */
private const val VISIBLE_LIMIT = 30

/**
 * 运动子应用首屏（界面定稿：节奏型）。
 *
 * 页面只复用已有字段与派生数据：本周概览 → 三类快记 → 最近记录 → 简报。
 * 青绿只用于模块识别、选中态与图表；保存与错误色继续走全局主题。
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
    val today by viewModel.today.collectAsStateWithLifecycle()
    val records by viewModel.sportRecords.collectAsStateWithLifecycle()
    val enabled = types.any { it.code == SportFields.TYPE_CODE && it.enabled }

    val sessions = remember(records) { SportStats.sessions(records) }
    val week = remember(sessions, today) { SportStats.week(sessions, today) }
    val brief = remember(sessions, today) { SportStats.brief(sessions, today) }
    val gap = remember(sessions, today) { SportStats.gapLabel(sessions.firstOrNull()?.date, today) }
    val recordById = remember(records) { records.associateBy { it.event.id } }
    val weekBySport = remember(sessions, week) {
        val inWeek = sessions.filter { !it.date.isBefore(week.start) && !it.date.isAfter(week.end) }
        SportFields.SPORTS.associateWith { sport ->
            val ofType = inWeek.filter { it.sport == sport }
            ofType.size to ofType.sumOf { it.minutes }
        }
    }

    val palette = sportPalette()
    val moduleColor = palette.accent
    val surface = MaterialTheme.colorScheme.surface
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
            .widthIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SportPageHeader(week = week, palette = palette, stacked = stacked, onBack = onBack)

        if (!enabled) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    text = "「运动」记录项目尚未启用。到「设置 → 记录项目」开启后，点首页的「运动」卡即可进入这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
            return@Column
        }

        SportWeekCard(
            week = week,
            gap = gap,
            moduleColor = moduleColor,
            surface = surface,
            stacked = stacked
        )

        SectionHeader(
            kicker = "今天怎么动",
            title = "选一种运动",
            hint = "点卡片直接记录",
            accent = moduleColor
        )
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SportFields.SPORTS.forEach { sport ->
                    SportQuickCard(
                        sport = sport,
                        last = SportStats.lastOf(sessions, sport),
                        weekCount = weekBySport.getValue(sport).first,
                        weekMinutes = weekBySport.getValue(sport).second,
                        accent = sportTypeColor(sport, moduleColor, surface),
                        stacked = true,
                        onClick = { onRecord(sport) }
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportFields.SPORTS.forEach { sport ->
                    SportQuickCard(
                        sport = sport,
                        last = SportStats.lastOf(sessions, sport),
                        weekCount = weekBySport.getValue(sport).first,
                        weekMinutes = weekBySport.getValue(sport).second,
                        accent = sportTypeColor(sport, moduleColor, surface),
                        stacked = false,
                        onClick = { onRecord(sport) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionHeader(
            kicker = "最近的节奏",
            title = "运动记录",
            hint = if (sessions.isEmpty()) "还没有" else "${visible.size} 条",
            accent = moduleColor
        )

        if (sessions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SportFilterChip(
                    label = "全部",
                    selected = filter.isEmpty(),
                    accent = moduleColor,
                    onClick = { filter = "" }
                )
                SportFields.SPORTS.forEach { sport ->
                    SportFilterChip(
                        label = SportFields.sportLabel(sport),
                        selected = filter == sport,
                        accent = moduleColor,
                        icon = sportIcon(sport),
                        onClick = { filter = if (filter == sport) "" else sport }
                    )
                }
            }
        }

        if (sessions.isEmpty()) {
            SportEmptyHint(
                text = "这段时间还没有运动记录。点上面任意一张类型卡就能记一次，记几天后这里会出现节奏与占比。"
            )
        } else if (visible.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = "这个类型近 ${SportStats.WINDOW_DAYS} 天还没有记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
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

        SportInsightSection(expanded = briefOpen, accent = moduleColor, onToggle = { briefOpen = !briefOpen }) {
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
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SportPageHeader(
    week: SportWeek,
    palette: SportPalette,
    stacked: Boolean,
    onBack: () -> Unit
) {
    val range = "${week.start.monthValue}/${week.start.dayOfMonth} - " +
        "${week.end.monthValue}/${week.end.dayOfMonth}"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回今天")
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "运动",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "不追配速，只留下身体真实走过的节奏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!stacked) {
                Surface(
                    shape = CircleShape,
                    color = palette.accentContainer
                ) {
                    Text(
                        text = "本周 $range",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onAccentContainer,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }
        if (stacked) {
            Surface(shape = CircleShape, color = palette.accentContainer) {
                Text(
                    text = "本周 $range · 近 ${SportStats.WINDOW_DAYS} 天",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onAccentContainer,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    kicker: String,
    title: String,
    hint: String? = null,
    accent: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = kicker,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
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
}

@Composable
private fun SportFilterChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.14f),
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent
        )
    )
}