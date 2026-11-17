package com.example.lifetrack.core.model

import java.time.LocalDate

/**
 * 「改结束日期」的纯计划（参考 App 的经期结束滚轮，见 `PeriodEndDatePicker`）。
 *
 * 背景：点了「大姨妈来了」之后每天会自动延续，如果那天忘了点「大姨妈走了」，
 * 后面几天都会被写成经期日，界面上的结束日就一直往后跑。手工改结束日要做的差异只有两类：
 *
 * - **补写** [fillDates]：新结束日晚于已记录末日时，把 `[段首..新结束日]` 里缺的日子补上；
 * - **撤掉** [trimDates]：新结束日早于已记录末日时，把 `(新结束日..旧末日]` 让出来。
 *
 * 这里只做日期算术，**不判断**哪些记录是「仅标记」可以删——那是调用方（ViewModel）的职责，
 * 用户填过流量 / 症状 / 日记的日子一律保留。纯 [LocalDate]，可被 `testDebugUnitTest` 直接覆盖。
 */
object PeriodEndEdit {

    /** [fillDates] 只含尚未记录的日期；[trimDates] 是新结束日之后、原段之内的全部日期。 */
    data class Plan(
        val fillDates: List<LocalDate>,
        val trimDates: List<LocalDate>
    )

    /**
     * @param segmentStart 本段经期首日（下界，改结束日不能早于它）
     * @param segmentEnd 本段已记录到的末日（进行中时传今天）
     * @param newEnd 用户选的新结束日；早于 [segmentStart] 时按 [segmentStart] 处理（调用方应先拦截）
     * @param recordedDates 已落库的经期日，用于跳过已有记录、不覆盖用户填过的内容
     */
    fun plan(
        segmentStart: LocalDate,
        segmentEnd: LocalDate,
        newEnd: LocalDate,
        recordedDates: Set<LocalDate>
    ): Plan {
        val end = if (newEnd.isBefore(segmentStart)) segmentStart else newEnd
        return Plan(
            fillDates = PeriodOngoing.span(segmentStart, end).filterNot { it in recordedDates },
            trimDates = PeriodOngoing.span(end.plusDays(1), segmentEnd)
        )
    }
}
