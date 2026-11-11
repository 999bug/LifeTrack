package com.example.lifetrack.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrack.core.security.ScreenCapturePolicy

/**
 * 设置类页面的统一骨架：分区标题 + 一张卡片 + 若干行。
 *
 * 之前设置页是 6 张各写一套内边距的卡片平铺，行高、字号、按钮位置都不统一，
 * 加一个模块就更长一截。这里把「标题 / 卡片 / 行 / 分隔线」收成四个组件，
 * 新加设置项只写一行，不再各自决定排版。
 */
internal val SettingsShape = RoundedCornerShape(16.dp)

@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            // 分组标题不参与层级竞争：11.5sp + 字距，靠位置和留白分组，不靠字号
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { heading() }
        )
        Card(
            shape = SettingsShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(content = content)
        }
    }
}

/**
 * 一行设置项。整行高 ≥56dp（满足 48dp 触摸目标），
 * 点击时涟漪覆盖整行；[enabled] 为 false 时降低不透明度并屏蔽点击。
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    role: Role = Role.Button,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val base = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val interactive = if (onClick != null && enabled) {
        base.clickable(role = role, onClick = onClick)
    } else {
        base
    }
    Row(
        modifier = interactive
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.45f),
        // 大字号下标题会换行，图标必须跟着首行而不是整行居中，否则会飘到两行文字中间
        verticalAlignment = if (LocalDensity.current.fontScale > 1.3f) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = if (LocalDensity.current.fontScale > 1.3f) 4.dp else 0.dp)
                    .size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** 行内说明：不占一整行标题，只作为卡片底部的补充文字。 */
@Composable
internal fun SettingsNote(text: String, isWarning: Boolean = false) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** 行尾的「进入下一页」指示；纯装饰，不加 contentDescription 以免读屏重复。 */
@Composable
internal fun ChevronTrailing() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 截屏遮挡的说明弹窗。应用锁与「亲密关系」模块触发的是同一个窗口级标志，
 * 文案只留一处，避免两个入口各说一套；也用于用户事后点状态行查原因。
 * 未遮挡时同样能打开，此时说明「当前可截屏」而不是错误地念出被禁止。
 */
@Composable
internal fun ScreenCaptureNoticeDialog(
    reasons: List<ScreenCapturePolicy.Reason>,
    suggestAppLock: Boolean = false,
    onOpenModules: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val blocked = reasons.isNotEmpty()
    val goToModules = onOpenModules?.takeIf {
        reasons.contains(ScreenCapturePolicy.Reason.INTIMACY_MODULE)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (blocked) "已禁止截屏与录屏" else "截屏与录屏正常") },
        text = {
            Text(
                if (blocked) {
                    buildString {
                        append("由于${ScreenCapturePolicy.describe(reasons)}，")
                        append("本应用的所有页面现在都无法截屏或录屏，最近任务预览也不显示内容。")
                        append("这是隐私保护设置生效，不是故障。")
                        if (suggestAppLock) {
                            append("\n\n这类记录较敏感，建议同时在「设置 → 安全」开启应用锁。")
                        }
                        append("\n\n如需恢复：关闭应用锁，并在「设置 → 记录项目」停用「亲密关系」；")
                        append("两项都关闭后立刻恢复。当前状态与原因可在「设置 → 安全」随时查看。")
                    }
                } else {
                    "当前没有开启应用锁，也没有启用「亲密关系」记录项目，可以正常截屏、录屏，" +
                        "最近任务预览也会显示页面内容。开启应用锁或启用该模块时会自动禁止，并再次提示。"
                }
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (goToModules != null) {
                    TextButton(onClick = {
                        onDismiss()
                        goToModules()
                    }) { Text("去关闭") }
                }
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        }
    )
}
