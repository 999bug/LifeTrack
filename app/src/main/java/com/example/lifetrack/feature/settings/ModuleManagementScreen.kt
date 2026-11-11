package com.example.lifetrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.security.ScreenCapturePolicy
import com.example.lifetrack.feature.home.MainViewModel

/**
 * 记录项目管理：从设置页拆出来的独立页面。
 * 设置页只留一行摘要，这里才有空间把启用状态、排序和解释放清楚。
 */
@Composable
fun ModuleManagementScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val types by viewModel.trackTypes.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val stacked = LocalDensity.current.fontScale > 1.3f
    // A10：首次启用高敏感模块时提示一次，不阻断启用动作。
    var intimacyNoticeShown by rememberSaveable { mutableStateOf(false) }
    var captureNoticeReasons by remember { mutableStateOf<List<ScreenCapturePolicy.Reason>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回设置"
                )
            }
            Text("记录项目", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = "启用后可在今天页记录；停用只隐藏新增入口，已有的历史记录、月历与统计不受影响。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        SettingsSection(title = "模块与顺序", modifier = Modifier.padding(start = 8.dp)) {
            if (types.isEmpty()) {
                Text(
                    text = "正在载入记录项目…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                types.forEachIndexed { index, type ->
                    if (index > 0) SettingsDivider()
                    ModuleRow(
                        type = type,
                        canMoveUp = index > 0,
                        canMoveDown = index < types.lastIndex,
                        stacked = stacked,
                        onToggle = { code, enabled ->
                            viewModel.setTypeEnabled(code, enabled)
                            if (code == IntimacyFields.TYPE_CODE && enabled && !intimacyNoticeShown) {
                                intimacyNoticeShown = true
                                // 回调里 types 还是启用前的值，显式按启用后的状态构造原因
                                val enabledTypes = types.map {
                                    if (it.code == IntimacyFields.TYPE_CODE) {
                                        it.copy(enabled = true)
                                    } else {
                                        it
                                    }
                                }
                                captureNoticeReasons = ScreenCapturePolicy.reasons(
                                    appLockEnabled = appLockEnabled == true,
                                    types = enabledTypes
                                )
                            }
                        },
                        onMove = viewModel::moveType
                    )
                }
            }
        }

        Text(
            text = "顺序决定今天页记录入口的排列；已启用 ${types.count { it.enabled }} / 共 ${types.size} 项。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }

    captureNoticeReasons?.let { reasons ->
        ScreenCaptureNoticeDialog(
            reasons = reasons,
            suggestAppLock = true,
            onDismiss = { captureNoticeReasons = null }
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleLabel(type = type, modifier = Modifier.weight(1f))
            if (!stacked) MoveButtons(type, canMoveUp, canMoveDown, onMove)
            ModuleSwitch(type = type, onToggle = onToggle)
        }
        if (stacked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "调整顺序",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                MoveButtons(type, canMoveUp, canMoveDown, onMove)
            }
        }
    }
}

/** 整行只点开关会很难点中，这里让开关本身保持 48dp 语义区域，并提供明确的状态朗读。 */
@Composable
private fun ModuleSwitch(type: TrackTypeEntity, onToggle: (String, Boolean) -> Unit) {
    Switch(
        checked = type.enabled,
        onCheckedChange = { onToggle(type.code, it) },
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics {
                contentDescription = "${type.name}记录开关"
                stateDescription = if (type.enabled) "已启用" else "已停用"
            }
    )
}

@Composable
private fun ModuleLabel(type: TrackTypeEntity, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = type.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "${categoryLabel(type.category)} · ${modeLabel(type.trackingMode)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 上下移按钮保持 48dp 触摸目标；禁用时仍占位，避免行高跳动。 */
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
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = "上移${type.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    IconButton(
        onClick = { onMove(type.code, 1) },
        enabled = canMoveDown,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = "下移${type.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
