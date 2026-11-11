package com.example.lifetrack.core.security

import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.IntimacyFields

/**
 * 截屏遮挡（FLAG_SECURE）的唯一判定口径。
 *
 * `MainActivity` 用它决定是否给窗口加标志，设置页用它向用户解释「为什么现在不能截屏」，
 * 两处必须共用同一份规则，不能各写一遍。遮挡是窗口级的：一旦生效，
 * 本应用所有页面都无法截屏 / 录屏，最近任务预览也不显示内容，
 * 不只是亲密关系相关页面。
 */
object ScreenCapturePolicy {

    /** 触发遮挡的原因，按开关所在位置排序，供 UI 直接展示。 */
    enum class Reason(val label: String) {
        APP_LOCK("应用锁已开启"),
        INTIMACY_MODULE("「亲密关系」记录项目已启用")
    }

    fun intimacyModuleEnabled(types: List<TrackTypeEntity>): Boolean =
        types.any { it.code == IntimacyFields.TYPE_CODE && it.enabled }

    fun reasons(appLockEnabled: Boolean, types: List<TrackTypeEntity>): List<Reason> = listOfNotNull(
        Reason.APP_LOCK.takeIf { appLockEnabled },
        Reason.INTIMACY_MODULE.takeIf { intimacyModuleEnabled(types) }
    )

    /** 与 [reasons] 共用同一份结果，避免「状态说没遮挡、原因却列了两条」的漂移。 */
    fun isBlocked(appLockEnabled: Boolean, types: List<TrackTypeEntity>): Boolean =
        reasons(appLockEnabled, types).isNotEmpty()

    /** 供提示文案使用的一句话原因，例如「应用锁已开启、『亲密关系』记录项目已启用」。 */
    fun describe(reasons: List<Reason>): String = reasons.joinToString("、") { it.label }
}
