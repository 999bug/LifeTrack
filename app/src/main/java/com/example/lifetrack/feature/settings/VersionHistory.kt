package com.example.lifetrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifetrack.BuildConfig

/**
 * 版本更新记录。
 *
 * 只写用户能感知到的变化，一句一条；内部重构、测试与流水线细节不进这里。
 * 列表按版本从新到旧排列，发版时在最前面补一条；当前安装的版本标「当前」。
 * 设置页只放一行「更新日志」入口，点开才看这份列表，避免十来个版本把页面撑长。
 */
internal data class VersionNote(
    val version: String,
    val date: String,
    val summary: String
)

internal val versionHistory: List<VersionNote> = listOf(
    VersionNote(
        version = "0.6.7",
        date = "2026-11-11",
        summary = "内部改进与稳定性修复"
    ),
    VersionNote(
        version = "0.6.6",
        date = "2026-11-11",
        summary = "安装包体积减至四分之一；设置页可整理数据库；更新检查改为每天一次"
    ),
    VersionNote(
        version = "0.6.5",
        date = "2026-11-11",
        summary = "设置页按使用频率重新排布，常用入口集中到主卡，数据与备份默认折叠"
    ),
    VersionNote(
        version = "0.6.4",
        date = "2026-11-11",
        summary = "截屏遮挡时在设置页显示当前状态与原因，点开即可查看恢复方法"
    ),
    VersionNote(
        version = "0.6.3",
        date = "2026-11-11",
        summary = "排便记录改用布里斯托图示选择；修复国内更新在同步期的误报"
    ),
    VersionNote(
        version = "0.6.2",
        date = "2026-11-11",
        summary = "月历可按天查看；首页改为模块网格与今日时间轴；主题支持浅色、深色与跟随系统"
    ),
    VersionNote(
        version = "0.6.1",
        date = "2026-11-10",
        summary = "新增亲密关系记录模块（默认停用）；发版版本号改为自动递增"
    ),
    VersionNote(
        version = "0.6.0",
        date = "2026-11-10",
        summary = "数据库与备份全面加密；新增应用锁、截屏防护与开源许可"
    ),
    VersionNote(
        version = "0.5.0",
        date = "2026-11-10",
        summary = "应用内更新支持 Gitee 国内镜像，GitHub 兜底"
    ),
    VersionNote(
        version = "0.4.0",
        date = "2026-11-09",
        summary = "设置页按记录、数据、外观、关于四段重构；补全无障碍语义与触摸目标"
    ),
    VersionNote(
        version = "0.3.0",
        date = "2026-11-09",
        summary = "新增运动模块，统一各页面的记录类型口径"
    ),
    VersionNote(
        version = "0.2.0",
        date = "2026-11-09",
        summary = "新增饮水、咖啡与茶记录；统计周/月/年改版；支持备份导出与应用内更新"
    ),
    VersionNote(
        version = "0.1.0",
        date = "2026-11-09",
        summary = "首个公开版本：记录、历史、统计与签名 APK 自动发布"
    )
)

/** 弹窗只列最近几个版本，完整历史仍随发版累积在 [versionHistory] 里。 */
private const val VISIBLE_VERSION_COUNT = 6

/** 更新日志弹窗：设置页只占一行入口，点开后列表自身可滚动。 */
@Composable
internal fun VersionHistoryDialog(onDismiss: () -> Unit) {
    val recent = versionHistory.take(VISIBLE_VERSION_COUNT)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新日志") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                recent.forEachIndexed { index, note ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    VersionHistoryEntry(note)
                }
                if (versionHistory.size > recent.size) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = "仅显示最近 $VISIBLE_VERSION_COUNT 个版本，完整历史随版本累积。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun VersionHistoryEntry(note: VersionNote) {
    val isCurrent = note.version == BuildConfig.VERSION_NAME
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 日期与「当前」标签先按自身宽度测量且强制单行，版本号拿剩余空间；
            // 大字号下只让版本号换行，不会把「当前」拆成两行或挤掉日期
            Text(
                text = "v${note.version}",
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            if (isCurrent) {
                Text(
                    text = "当前",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = note.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = note.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
