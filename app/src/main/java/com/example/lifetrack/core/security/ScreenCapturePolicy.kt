package com.example.lifetrack.core.security

import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.MenstruationFields

/**
 * 截屏遮挡（FLAG_SECURE）的唯一判定口径。
 *
 * `MainActivity` 用它决定是否给窗口加标志，设置页用它向用户解释「为什么现在不能截屏」，
 * 两处必须共用同一份规则，不能各写一遍。遮挡是窗口级的：一旦生效，
 * 本应用所有页面都无法截屏 / 录屏，最近任务预览也不显示内容，不只是高敏感模块相关页面。
 *
 * 高敏感类型由 [HIGH_SENSITIVITY_TYPES] 数据驱动（A10 亲密关系、A9 月经）；新增模块只在
 * [moduleReasons] 加一行并补一个 [Reason]，判定与各处文案不必再逐一硬改。
 */
object ScreenCapturePolicy {

    /** 触发遮挡的原因，按开关所在位置排序，供 UI 直接展示。[moduleName] 仅模块原因有值。 */
    enum class Reason(val label: String, val moduleName: String? = null) {
        APP_LOCK("应用锁已开启"),
        INTIMACY_MODULE("「亲密关系」记录项目已启用", "亲密关系"),
        MENSTRUATION_MODULE("「月经」记录项目已启用", "月经")
    }

    /** 高敏感类型 → 原因，按固定展示顺序；新增模块只加一行。 */
    private val moduleReasons = listOf(
        IntimacyFields.TYPE_CODE to Reason.INTIMACY_MODULE,
        MenstruationFields.TYPE_CODE to Reason.MENSTRUATION_MODULE
    )

    val HIGH_SENSITIVITY_TYPES: Set<String> = moduleReasons.map { it.first }.toSet()

    /** 全部高敏感模块名，供「未遮挡」文案遍历生成，避免硬编码某一个模块。 */
    val sensitiveModuleNames: List<String> = moduleReasons.mapNotNull { it.second.moduleName }

    fun reasons(appLockEnabled: Boolean, types: List<TrackTypeEntity>): List<Reason> {
        val enabledCodes = types.filter { it.enabled }.map { it.code }.toSet()
        return buildList {
            if (appLockEnabled) add(Reason.APP_LOCK)
            moduleReasons.forEach { (code, reason) -> if (code in enabledCodes) add(reason) }
        }
    }

    /** 与 [reasons] 共用同一份结果，避免「状态说没遮挡、原因却列了两条」的漂移。 */
    fun isBlocked(appLockEnabled: Boolean, types: List<TrackTypeEntity>): Boolean =
        reasons(appLockEnabled, types).isNotEmpty()

    /** 供提示文案使用的一句话原因，例如「应用锁已开启、『亲密关系』记录项目已启用」。 */
    fun describe(reasons: List<Reason>): String = reasons.joinToString("、") { it.label }
}