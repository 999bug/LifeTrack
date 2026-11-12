package com.example.lifetrack.feature.menstruation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.model.MenstruationFields

/**
 * 症状多选 chip 组：固定 12 项白名单，自动换行、不横向滚动（A7 教训），最多 8 项。
 *
 * 编辑器弹层与子应用逐行表单共用同一组 chip，保证两条写入路径的症状口径一致；
 * 白名单与上限都取自 [MenstruationFields]，不在这里另写一份。
 * 达到上限后未选中的 chip 禁用，已选中的仍可点取消。圆点图示与子应用同一套。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomChips(
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MenstruationFields.SYMPTOM_OPTIONS.forEach { (code, label) ->
            val isSelected = code in selected
            val canAdd = selected.size < MenstruationFields.MAX_SYMPTOMS
            FilterChip(
                selected = isSelected,
                enabled = enabled && (isSelected || canAdd),
                onClick = { onToggle(code) },
                label = { Text(label) },
                leadingIcon = {
                    PeriodSymptomDot(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        size = 10.dp
                    )
                }
            )
        }
    }
}