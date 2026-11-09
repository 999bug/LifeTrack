package com.example.lifetrack.feature.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 单选整数值（含「未记录」），供各类型详情共用。 */
@Composable
fun ChoiceRow(
    labels: List<String>,
    values: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("未记录") })
        labels.zip(values).forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(if (selected == value) null else value) },
                label = { Text(label) }
            )
        }
    }
}

/** 三态布尔值（未记录 / 否 / 是），供各类型详情共用。 */
@Composable
fun TriStateRow(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text("未记录") })
            FilterChip(selected = value == false, onClick = { onChange(false) }, label = { Text("否") })
            FilterChip(selected = value == true, onClick = { onChange(true) }, label = { Text("是") })
        }
    }
}
