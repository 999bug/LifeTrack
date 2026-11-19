package com.example.lifetrack.feature.coffee

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 饮品选项卡片：单选一组，两列排布，支持角标、副标题、价格说明。
 * 用 heightIn 让内容撑高，配合大字号换行，不写固定 dp 高度（避免 200% 字体裁字）。
 */
@Composable
internal fun OptionCardGroup(
    values: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
    description: (String) -> String? = { null },
    badge: (String) -> String? = { null },
    badgePrimary: (String) -> Boolean = { true },
    price: (String) -> String? = { null }
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { value ->
                    OptionCard(
                        value = value,
                        selected = value == selected,
                        label = label(value),
                        description = description(value),
                        badge = badge(value),
                        badgePrimary = badgePrimary(value),
                        price = price(value),
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(value) }
                    )
                }
                if (row.size == 1) {
                    // 奇数个时留一个等宽空位，保持两列对齐。
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    value: String,
    selected: Boolean,
    label: String,
    description: String?,
    badge: String?,
    badgePrimary: Boolean,
    price: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bigFont = LocalDensity.current.fontScale > 1.3f
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (bigFont) 76.dp else 62.dp),
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badgePrimary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (price != null) {
                Text(
                    price,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
