package com.example.lifetrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.entity.TrackTypeEntity

/** 设置页的「记录项目」卡片：启用/停用与排序，停用不影响已有历史记录。 */
@Composable
fun ModuleSettingsCard(
    types: List<TrackTypeEntity>,
    onToggle: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("记录项目", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "启用后可在今天页记录；停用不影响已有的历史记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (types.isEmpty()) {
                Text(
                    text = "正在载入记录项目…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                types.forEachIndexed { index, type ->
                    ModuleRow(
                        type = type,
                        canMoveUp = index > 0,
                        canMoveDown = index < types.lastIndex,
                        stacked = stacked,
                        onToggle = onToggle,
                        onMove = onMove
                    )
                }
            }
            Text(
                text = "已启用 ${types.count { it.enabled }} / 共 ${types.size} 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModuleRow(
    type: TrackTypeEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    stacked: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit
) {
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleLabel(type = type, modifier = Modifier.weight(1f))
                Switch(checked = type.enabled, onCheckedChange = { onToggle(type.code, it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MoveButtons(
                    type = type,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMove = onMove
                )
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ModuleLabel(type = type, modifier = Modifier.weight(1f))
            MoveButtons(
                type = type,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMove = onMove
            )
            Switch(checked = type.enabled, onCheckedChange = { onToggle(type.code, it) })
        }
    }
}

@Composable
private fun ModuleLabel(type: TrackTypeEntity, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = type.name, style = MaterialTheme.typography.titleSmall)
        Text(
            text = "${categoryLabel(type.category)} · ${modeLabel(type.trackingMode)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MoveButtons(
    type: TrackTypeEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (String, Int) -> Unit
) {
    IconButton(
        onClick = { onMove(type.code, -1) },
        enabled = canMoveUp,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移${type.name}")
    }
    IconButton(
        onClick = { onMove(type.code, 1) },
        enabled = canMoveDown,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移${type.name}")
    }
}

private fun categoryLabel(category: String): String = when (category) {
    "body" -> "身体"
    "drink" -> "饮食"
    "activity" -> "活动"
    else -> category
}

private fun modeLabel(mode: String): String = when (mode) {
    "EVENT" -> "事件记录"
    else -> mode
}
