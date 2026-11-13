package com.example.lifetrack.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.model.CycleSnapshot
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.Phase
import com.example.lifetrack.core.ui.CycleRing
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.core.ui.cycleVisualColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 首页通栏「周期概览卡」（界面定稿 v2 · 批 1）。
 *
 * 月经卡不再是网格里一张「打开子应用」的方块：它语义与旁边「记一条」的卡片不同，
 * 混在双列网格里既拉不齐高度，也让最重要的周期状态被压成一行小字。这里升级为通栏卡，
 * 用 64dp 迷你环把「今天在周期哪里」画出来，整卡点击进入经期子应用。
 *
 * 所有数字都从 [CycleSnapshot] / 进行中起点派生，不落库、不产出健康结论。
 */
@Composable
internal fun CycleOverviewCard(
    snapshot: CycleSnapshot,
    ongoingStart: LocalDate?,
    cycleDays: Int,
    periodDays: Int,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = cycleVisualColors()
    val periodColor = LifeTrackTokens.ModuleColors.of(MenstruationFields.TYPE_CODE)
    val stacked = LocalDensity.current.fontScale > 1.3f

    val cycleLength = snapshot.medianCycleDays ?: cycleDays
    val periodLength = snapshot.medianPeriodDays ?: periodDays
    val cycleStart = ongoingStart ?: snapshot.segments.lastOrNull()?.start
    val dayIndex = if (cycleStart == null) {
        1
    } else {
        (ChronoUnit.DAYS.between(cycleStart, today).toInt() + 1).coerceIn(1, cycleLength)
    }
    val recordedDays = when {
        ongoingStart != null -> ChronoUnit.DAYS.between(ongoingStart, today).toInt() + 1
        else -> snapshot.segments.lastOrNull()?.lengthDays ?: 0
    }.coerceAtLeast(0)

    val nextStart = snapshot.predictedPeriods.firstOrNull()?.start
    val nextText = if (nextStart != null) {
        "预计 ${cn(nextStart)} 下一次 · 周期 $cycleLength 天"
    } else if (snapshot.sampleReliable) {
        "周期 $cycleLength 天 · 继续记录可给出预计日期"
    } else {
        "记录几个周期后自动预测 · 周期 $cycleLength 天"
    }
    val ongoingDays = ongoingStart?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 }
    val phase = snapshot.currentPhase
    val badge: String
    val headline: String
    when {
        ongoingStart != null -> {
            badge = "周期概览 · 经期中"
            headline = "经期第 $ongoingDays 天"
        }

        phase is Phase.InPeriod -> {
            badge = "周期概览 · 经期中"
            headline = "经期第 ${phase.dayIndex} 天"
        }

        phase is Phase.BeforeNext -> {
            badge = "周期概览"
            headline = "距预计下次经期 ${phase.days} 天"
        }

        phase is Phase.Delayed -> {
            badge = "周期概览 · 已推迟"
            headline = "已推迟 ${phase.days} 天"
        }

        snapshot.segments.isNotEmpty() -> {
            badge = "周期概览 · 本次已结束"
            headline = "本次经期已结束"
        }

        else -> {
            badge = "周期概览"
            headline = "记录暂不足"
        }
    }

    val summary = "$badge，$headline，$nextText。点击进入经期子应用"

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = summary
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = periodColor.copy(alpha = 0.09f)),
        border = BorderStroke(1.dp, periodColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CycleRing(
                dayIndex = dayIndex,
                cycleLength = cycleLength,
                periodLength = periodLength,
                size = 64.dp,
                showCaption = false,
                colors = colors,
                contentDescription = "周期第 $dayIndex 天，共 $cycleLength 天，经期 $periodLength 天，已记录 $recordedDays 天"
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = periodColor
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = nextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                )
            }
            if (!stacked) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = periodColor
                )
            }
        }
    }
}

private fun cn(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"
