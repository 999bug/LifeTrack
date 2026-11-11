package com.example.lifetrack.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.ui.LifeTrackTokens

/**
 * 首页的模块卡：一张卡同时承担「看今天记了几次」和「再记一次」。
 *
 * 整卡点击 = 该模块最常用的动作：
 * - 有合理默认值的（排便、饮水）直接落一条记录，不弹编辑器；
 * - 没有默认值的（咖啡、运动）打开编辑器，因为份量 / 时长必须由用户填。
 *
 * 卡片右下角的「详细」只出现在快捷记录型卡片上，用于明确进入编辑器；
 * 其余卡片整卡点击本身就是编辑，不再重复提供入口。
 */
@Composable
internal fun ModuleCard(
    type: TrackTypeEntity,
    count: Int,
    summary: String?,
    quickAction: Boolean,
    onRecord: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LifeTrackTokens.ModuleColors.of(type.code)
    val meta = buildString {
        append("今日 $count 次")
        if (!summary.isNullOrBlank()) {
            append(" · ")
            append(summary)
        }
    }

    Card(
        onClick = onRecord,
        modifier = modifier,
        shape = LifeTrackTokens.Shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Box {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // 3dp 识别色条：整个模块色系统里面积最大的一处，只到这里为止
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.85f))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = LifeTrackTokens.Spacing.module,
                            end = LifeTrackTokens.Spacing.module,
                            top = 12.dp,
                            bottom = LifeTrackTokens.Spacing.module
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(LifeTrackTokens.Shapes.icon)
                            .background(accent.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = LifeTrackTokens.ModuleIcons.of(type.code),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = type.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (quickAction) {
                            // 用带最小尺寸的可点区域而不是裸文字：11sp 的「详细」字面只有 22×14dp，
                            // 直接 clickable 会让触摸目标小到点不中。宽度用 widthIn 而不是固定值，
                            // 否则 200% 字体下「详细」会被压成「详」。
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .widthIn(min = 44.dp)
                                    .heightIn(min = 32.dp)
                                    .clickable(onClick = onDetail)
                                    .semantics { role = Role.Button }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "详细",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 右上角的加号只是提示「这里能加一条」，不做独立点击区，避免和整卡动作打架
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** 首页模块网格的单列宽度计算：大字号下退回一列，避免两列把模块名挤成竖排。 */
internal fun moduleColumns(fontScale: Float): Int = if (fontScale > 1.3f) 1 else 2
