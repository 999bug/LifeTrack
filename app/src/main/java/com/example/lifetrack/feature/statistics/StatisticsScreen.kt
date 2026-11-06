package com.example.lifetrack.feature.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.feature.home.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsScreen(viewModel: MainViewModel) {
    val stats by viewModel.weeklyStats.collectAsStateWithLifecycle()
    val total = stats.sumOf { it.count }
    val average = if (stats.isEmpty()) 0.0 else total.toDouble() / stats.size
    val maxCount = (stats.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("统计", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard("近 7 天", total.toString(), Modifier.weight(1f))
            SummaryCard("日均次数", String.format("%.1f", average), Modifier.weight(1f))
        }
        Text("最近 7 天排便次数", style = MaterialTheme.typography.titleLarge)
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                stats.forEach { item ->
                    val dateLabel = runCatching {
                        LocalDate.parse(item.localDate).format(DateTimeFormatter.ofPattern("M/d EEE"))
                    }.getOrDefault(item.localDate)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(dateLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Box(
                            modifier = Modifier.weight(2f).height(14.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(item.count.toFloat() / maxCount)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            )
                        }
                        Text(item.count.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Text(
            "统计仅根据已保存的记录计算，不构成医疗诊断。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
