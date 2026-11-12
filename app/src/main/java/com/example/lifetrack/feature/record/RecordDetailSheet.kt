package com.example.lifetrack.feature.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.DetailField
import com.example.lifetrack.core.model.toDetailView
import com.example.lifetrack.core.security.SensitiveTypes

/**
 * 记录详情底部弹窗：只读，不提供编辑 / 删除。
 *
 * 内容由 [toDetailView] 一次性展开成「分区 → 键值行」，UI 不参与字段映射，
 * 避免列表卡片与详情弹窗出现两套文案。整卡可点的入口在 RecordRow。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailSheet(
    record: BackupRecord,
    onDismiss: () -> Unit
) {
    val view = remember(record) { record.toDetailView() }
    // 200% 字体下左右两栏会把长值挤成多行重叠，按项目既有做法改为上下堆叠。
    val stacked = LocalDensity.current.fontScale > 1.3f
    // A10 / A9 L1：详情由用户主动点开，视觉上照常展示；读屏只念一句汇总，
    // 不逐字段朗读「保护措施：是」「流量：中」这类内容。关闭按钮留在收敛区之外，仍可操作。
    // 判定按高敏感集合数据驱动，新增敏感模块不必再改这里。
    val privacySemantics = if (record.event.typeCode in SensitiveTypes.HIGH_SENSITIVITY_TYPES) {
        Modifier.clearAndSetSemantics {
            contentDescription = "已记录一条敏感记录，详情请查看屏幕内容"
        }
    } else {
        Modifier
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().then(privacySemantics)) {
                Text(
                    text = view.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                view.sections.forEachIndexed { index, section ->
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    section.fields.forEach { field ->
                        DetailLine(field = field, stacked = stacked)
                    }
                    if (index < view.sections.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
            ) { Text("关闭") }
        }
    }
}

@Composable
private fun DetailLine(field: DetailField, stacked: Boolean) {
    // 完整 UUID 这类长值在左右两栏里会折出一个孤字，单独给它整行宽度。
    val useStacked = stacked || field.value.length > 28
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (field.muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // 读屏把一整行念成一句，而不是「标签」和「值」两个无关联节点。
    val rowSemantics = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "${field.label}：${field.value}"
    }

    if (useStacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(rowSemantics)
                .padding(vertical = 5.dp)
        ) {
            Text(field.label, style = MaterialTheme.typography.bodySmall, color = labelColor)
            Text(field.value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(rowSemantics)
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier.weight(0.38f)
            )
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.62f)
            )
        }
    }
}
