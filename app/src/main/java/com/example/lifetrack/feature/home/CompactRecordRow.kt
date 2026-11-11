package com.example.lifetrack.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.ui.LifeTrackTokens
import com.example.lifetrack.feature.record.RecordSummary

/**
 * 首页时间轴上的一行。
 *
 * 比历史页的卡片更紧凑：时间、色点、类型、一行摘要，编辑与删除收进行尾菜单，
 * 让一屏能多看两三条。点击整行仍然打开只读详情，与历史页行为一致。
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
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
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
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
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
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
    }
}
