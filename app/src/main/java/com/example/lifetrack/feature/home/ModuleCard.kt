package com.example.lifetrack.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.ui.LifeTrackTokens

/**
 * 首页模块卡（界面定稿 v2 · 批 1）。
 *
 * 之前「框大小不一致」的第二处根因在这里：只有快捷记录 / 导航型卡片才画 32dp 动作区，
 * 同行卡片因此差 16dp。现在改成**所有卡片常驻同一块动作槽**，没有快捷动作的卡也占位，
 * 卡片本身由调用方统一给 [cardHeight]（100% 字体 128dp，大字体 176dp），三段骨架固定：
 * 图标 + 次数 → 名称 → 状态行 + 动作槽。
 *
 * 整卡点击 = 该模块最常用的动作（有默认值的直接落一条，没有默认值的打开编辑器）；
 * 右下角动作槽与整卡同高占位，但可独立点击：
 * - 快捷记录型（排便 / 饮水）在这里保留「详细」入口，避免丢掉「新增一条带详情的记录」；
 * - 其余模块的槽就是整卡动作本身，文案由调用方按模块给出（「记一杯」「记一次」…）。
 *
 * 3dp 侧色条与右上角不可点的「+」已删除：前者是重复染色，后者是假按钮。
 * [wide] 用于奇数模块时的最后一张通栏卡，高度仍与其他卡一致，只是内部改横向排布。
 */
@Composable
internal fun ModuleCard(
    type: TrackTypeEntity,
    count: Int,
    summary: String?,
    actionLabel: String,
    onRecord: () -> Unit,
    onAction: () -> Unit,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    wide: Boolean = false
) {
    val accent = LifeTrackTokens.ModuleColors.of(type.code)
    val stacked = LocalDensity.current.fontScale > 1.3f
    val slotHeight = if (stacked) 36.dp else 26.dp
    val status = when {
        !summary.isNullOrBlank() && count > 0 -> "$summary · $count 次"
        !summary.isNullOrBlank() -> summary
        count > 0 -> "今日 $count 次"
        else -> "今天还没记录"
    }

    Card(
        onClick = onRecord,
        modifier = modifier.height(cardHeight),
        shape = LifeTrackTokens.Shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModuleIcon(type = type, accent = accent)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModuleName(type = type)
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ActionSlot(
                    label = actionLabel,
                    accent = accent,
                    height = slotHeight,
                    onClick = onAction,
                    description = "${type.name}：$actionLabel"
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ModuleIcon(type = type, accent = accent)
                    Text(
                        text = "$count 次",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                ModuleName(type = type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = slotHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ActionSlot(
                        label = actionLabel,
                        accent = accent,
                        height = slotHeight,
                        onClick = onAction,
                        description = "${type.name}：$actionLabel"
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleIcon(type: TrackTypeEntity, accent: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = LifeTrackTokens.ModuleIcons.of(type.code),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ModuleName(type: TrackTypeEntity) {
    Text(
        text = type.name,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 常驻动作槽：最小 44 × 26dp，所有卡片都有，是等高的关键。 */
@Composable
private fun ActionSlot(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    height: Dp,
    onClick: () -> Unit,
    description: String
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = Modifier
            .widthIn(min = 44.dp)
            .height(height)
            .clip(shape)
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                color = accent
            )
        }
    }
}

/** 首页模块网格的单列宽度计算：大字号下退回一列，避免两列把模块名挤成竖排。 */
internal fun moduleColumns(fontScale: Float): Int = if (fontScale > 1.3f) 1 else 2
