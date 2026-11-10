package com.example.lifetrack.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.core.security.DatabaseKeyStore

/**
 * 密钥不可用时展示的终止页：不新建空库、不清除数据，明确告诉用户数据仍在本地。
 * 完整文案与「从备份恢复」入口在 C6 补齐。
 */
@Composable
fun DatabaseUnavailableScreen(failure: DatabaseKeyStore.Failure) {
    LifeTrackTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "无法解锁本地数据",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "密钥校验未通过，本地记录仍在设备上，不会被自动清除。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请不要卸载应用或清除应用数据，否则记录会永久丢失。重启设备后若仍无法解锁，需要从之前导出的备份恢复。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "错误代码：$failure",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
