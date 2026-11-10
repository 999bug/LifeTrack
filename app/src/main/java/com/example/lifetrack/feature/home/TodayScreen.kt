package com.example.lifetrack.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.feature.record.RecordRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val todayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")

@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    onQuickRecord: () -> Unit,
    onDetailedRecord: () -> Unit,
    onWaterRecord: () -> Unit,
    onCoffeeRecord: () -> Unit,
    onSportRecord: () -> Unit,
    onIntimacyRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowDetail: (BackupRecord) -> Unit,
    onEdit: (BackupRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val records by viewModel.todayRecords.collectAsStateWithLifecycle()
    val trackTypes by viewModel.trackTypes.collectAsStateWithLifecycle()
    val enabledTypes = trackTypes.filter { it.enabled }
    // 首页、历史、月历与统计默认都按「全部类型」同一口径计数，这里不再只数排便。
    val countsByType = records.groupingBy { it.event.typeCode }.eachCount()
    val today = LocalDate.now().format(todayFormatter)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今天", style = MaterialTheme.typography.headlineMedium)
                Text(today, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            // 读屏只念一句汇总，不逐个念「排便 0 次 / 饮水 0 次」四遍
            val countSummary = buildString {
                append("今日共 ${records.size} 次")
                enabledTypes.forEach { append("，${it.name} ${countsByType[it.code] ?: 0} 次") }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = countSummary
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("今日记录", style = MaterialTheme.typography.titleMedium)
                    Row {
                        Text(
                            records.size.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "次",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(start = 6.dp, top = 18.dp)
                        )
                    }
                    if (enabledTypes.isEmpty()) {
                        Text(
                            "没有启用的记录项目",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        enabledTypes.chunked(2).forEach { rowTypes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowTypes.forEach { type ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            type.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                        )
                                        Text(
                                            "${countsByType[type.code] ?: 0} 次",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item {
            if (enabledTypes.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("当前没有启用的记录项目", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "可在设置 → 记录项目中启用后继续记录。已有的历史记录不受影响。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onOpenSettings) { Text("去设置") }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 按「记录项目」页设置的顺序渲染入口，排序才是真的生效
                    enabledTypes.forEach { type ->
                        when (type.code) {
                            DEFAULT_POOP_TYPE.code -> Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(onClick = onQuickRecord, modifier = Modifier.weight(1f)) { Text("快速打卡") }
                                OutlinedButton(onClick = onDetailedRecord, modifier = Modifier.weight(1f)) { Text("详细记录") }
                            }

                            WaterFields.TYPE_CODE -> OutlinedButton(
                                onClick = onWaterRecord,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("记录饮水") }

                            CoffeeFields.TYPE_CODE -> OutlinedButton(
                                onClick = onCoffeeRecord,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("记录咖啡 / 茶") }

                            SportFields.TYPE_CODE -> OutlinedButton(
                                onClick = onSportRecord,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("记录运动") }

                            IntimacyFields.TYPE_CODE -> OutlinedButton(
                                onClick = onIntimacyRecord,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("记录亲密关系") }
                        }
                    }
                    Text(
                        "快速打卡会立即保存当前时间；饮水 / 咖啡的份量、运动的时长为必填，亲密关系三项均可不填。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { Text("今日记录", style = MaterialTheme.typography.titleLarge) }
        if (records.isEmpty()) {
            item {
                Text("今天还没有记录。完成第一次打卡后，会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(records, key = { it.event.id }) { record ->
                RecordRow(
                    record = record,
                    onShowDetail = { onShowDetail(record) },
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record.event.id) }
                )
            }
        }
    }
}
