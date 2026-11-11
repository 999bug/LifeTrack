package com.example.lifetrack.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.lifetrack.BuildConfig

/**
 * 版本更新记录。
 *
 * 只写用户能感知到的变化，一句一条；内部重构、测试与流水线细节不进这里。
 * 列表按版本从新到旧排列，发版时在最前面补一条——当前安装的版本会标「当前」，
 * 所以新版发布后忘了补记录会立刻在设置页露馅。
 */
internal data class VersionNote(
    val version: String,
    val date: String,
    val summary: String
)

internal val versionHistory: List<VersionNote> = listOf(
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

/** 一行一个版本；版本号与日期同行，摘要单独一行，200% 字体下也只会往下长。 */
@Composable
internal fun VersionHistoryList() {
    versionHistory.forEachIndexed { index, note ->
        if (index > 0) SettingsDivider()
        val isCurrent = note.version == BuildConfig.VERSION_NAME
        SettingsRow(
            title = if (isCurrent) "v${note.version} · 当前" else "v${note.version}",
            subtitle = note.summary,
            titleColor = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            trailing = {
                Text(
                    text = note.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
