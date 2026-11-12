package com.example.lifetrack.core.security

import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields

/**
 * 高敏感记录类型的唯一登记处。
 *
 * 这里只解决「哪些类型算高敏感」这一个问题，供两处呈现层收敛使用：
 * 列表与详情不拼字段原文、明文导出在风险确认里点名这些模块。
 *
 * **不再与截屏相关**：0.6.x 起移除了 `FLAG_SECURE`，应用锁与高敏感模块都不会再禁止
 * 截屏 / 录屏，窗口上没有遮挡标志，也就没有「已禁止 / 正常」这层状态。
 * 是否把截图发给别人，交由使用者自己判断。
 *
 * 新增高敏感模块只在这里加一行。
 */
object SensitiveTypes {

    private val entries = listOf(
        IntimacyFields.TYPE_CODE to "亲密关系",
        MenstruationFields.TYPE_CODE to "月经"
    )

    val HIGH_SENSITIVITY_TYPES: Set<String> = entries.map { it.first }.toSet()

    /** 固定顺序的模块名，供「明文导出会包含…」这类文案遍历生成。 */
    val sensitiveModuleNames: List<String> = entries.map { it.second }
}
