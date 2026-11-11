package com.example.lifetrack.feature.poop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.ui.LifeTrackTokens

/**
 * 「七型对照」说明弹层：把七种形态按含水量轴分三段列全，供首次使用或拿不准时查。
 * 只做自我观察的说明，不给诊断结论；图形与编辑器共用 BristolArt，避免两套画法走样。
 */
@Composable
fun BristolGuideDialog(onDismiss: () -> Unit) {
    val artColor = LifeTrackTokens.ModuleColors.of(DEFAULT_POOP_TYPE.code)
    val creaseColor = MaterialTheme.colorScheme.surfaceContainerLow

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("七型对照") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "按外形和稠度选最接近的一型。1→7 是含水量从少到多的连续刻度，不是七种并列的分类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BristolCatalog.groups.forEach { (groupLabel, range) ->
                    Text(
                        "$groupLabel · ${range.first}–${range.last} 型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    range.forEach { number ->
                        val type = BristolCatalog.of(number) ?: return@forEach
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BristolArt(
                                number = number,
                                color = artColor,
                                creaseColor = creaseColor,
                                modifier = Modifier.size(56.dp, 34.dp)
                            )
                            Text(
                                "$number 型",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(min = 40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(type.shortLabel, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    type.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    "仅用于自我观察记录。持续异常或伴随其它不适，请咨询医生。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}
