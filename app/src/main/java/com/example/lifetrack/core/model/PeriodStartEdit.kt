package com.example.lifetrack.core.model

import java.time.LocalDate

/**
 * 「把某天设为开始日」的纯计划。
 *
 * 一段经期的首日由记录派生（连续记录日并成段），所以「改开始日」只能落到记录层面：
 * 把 `[旧首日..新首日-1]` 里由系统自动标记出来的日子撤掉，首日自然前移到用户选的那天。
 *
 * 两类日子不能碰：
 * - 用户填过流量 / 症状 / 日记的日子（[Plan.blockedDates] 非空时调用方必须整体拒绝，
 *   而不是「能删的删掉、剩下的留着」——那会留下 5–8 日这样的孤立段，把统计搅乱）；
 * - 新首日之后的日子（本来就不该动）。
 *
 * 这里只做日期算术，判断哪天「填过内容」由调用方（ViewModel）传入 [lockedDates]。
 */
object PeriodStartEdit {

    /**
     * @param dropDates 需要撤掉的仅标记日子，即 `[旧首日..新首日-1]` 里没有内容的日子
     * @param blockedDates `[旧首日..新首日-1]` 里填过内容的日子；非空表示这次改不动
     */
    data class Plan(
        val dropDates: List<LocalDate>,
        val blockedDates: List<LocalDate>
    ) {
        val canApply: Boolean get() = blockedDates.isEmpty()
    }

    /**
     * @param segmentStart 本段经期当前首日
     * @param newStart 用户选的新首日；早于或等于 [segmentStart] 时不产生任何改动
     * @param lockedDates 填过内容（详情 / 备注）的日子集合
     */
    fun plan(
        segmentStart: LocalDate,
        newStart: LocalDate,
        lockedDates: Set<LocalDate>
    ): Plan {
        if (!newStart.isAfter(segmentStart)) return Plan(emptyList(), emptyList())
        val range = PeriodOngoing.span(segmentStart, newStart.minusDays(1))
        return Plan(
            dropDates = range.filterNot { it in lockedDates },
            blockedDates = range.filter { it in lockedDates }
        )
    }
}
