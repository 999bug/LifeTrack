package com.example.lifetrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrack.feature.home.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Card {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("深色模式", style = MaterialTheme.typography.titleMedium)
                    Text("仅影响应用显示，不改变记录数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
        }
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据与隐私", style = MaterialTheme.typography.titleMedium)
                Text("记录仅保存在本应用的私有存储中。此版本不申请网络权限，也不提供账号或云同步。", style = MaterialTheme.typography.bodyMedium)
                Text("系统自动云备份和设备迁移已通过 Manifest 与规则文件排除应用数据。卸载应用或清除应用数据可能造成记录丢失。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("主动导出 / 恢复尚未实现；请勿把当前版本当作长期备份工具。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于生活记", style = MaterialTheme.typography.titleMedium)
                Text("版本 0.1.0 · 本地生活记录工具")
                Text("本产品用于个人记录与回顾，不提供医疗诊断。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
