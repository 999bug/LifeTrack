package com.example.lifetrack.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.record.RecordSummary

/**
 * 首页时间轴上的一行（界面定稿 v2 · 批 1）。
 *
 * 之前只是「时间 + 色点 + 文字」的紧凑列表，看不出先后顺序；现在左侧 18dp 轨道画
 * 竖线与节点，行与行首尾相接形成连续时间轴。点击整行仍打开只读详情，编辑与删除收在
 * 行尾菜单里；菜单按钮从 36dp 提到 44dp（批 3 · 触摸目标）。
 */
@Composable
internal fun CompactRecordRow(
    record: BackupRecord,
    typeNames: Map<String, String>,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean = true
) {
    val time = RecordSummary.time(record)
    val typeName = RecordSummary.typeName(record.event.typeCode, typeNames)
    val detail = RecordSummary.detail(record)
    val dotColor = LifeTrackTokens.ModuleColors.of(record.event.typeCode)
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val backgroundColor = MaterialTheme.colorScheme.background
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val x = 6.dp.toPx()
                    val nodeY = 20.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, this.size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(backgroundColor, radius = 6.5.dp.toPx(), center = Offset(x, nodeY))
                    drawCircle(dotColor, radius = 4.5.dp.toPx(), center = Offset(x, nodeY))
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onShowDetail)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$time，$typeName，$detail"
                    }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(42.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
    }
}
