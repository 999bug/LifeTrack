package com.example.lifetrack.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 「点了来了就自动延续」的纯函数（设计 §5.2 的增量）。
 *
 * 交互口径与参考 App 一致：点一次「大姨妈来了」记下起点，之后**不需要每天操作**，
 * 日历把 `[起点..今天]` 整段显示为经期；只有点「大姨妈走了」才结束。
 *
 * 派生与落库分开，是为了不写「提前标记未来」的脏数据：
 * - 显示 / 周期推算：用 [span] 把起点到今天展开，与已有记录取并集；
 * - 落库：[pendingWrites] 只把**昨天及更早**的缺失日补成「仅标记」事件，今天永远由派生显示。
 *   这样「今天」不会在用户还没过完这天时就先入库，点「走了」时也不会留下多余记录。
 *
 * 不涉及时刻与时区，只用 [LocalDate]，可被 `testDebugUnitTest` 直接覆盖。
 */
object PeriodOngoing {

    /**
     * 自动延续的最长跨度，与 [PeriodFill.MAX_GAP_DAYS] 同源。
     * 超过这个天数还点「来了」，多半是在补记一段很老的经期，只标当天、不向后延续，
     * 避免误触把整月染成经期。
     */
    const val MAX_AUTO_DAYS = PeriodFill.MAX_GAP_DAYS

    /** 起点距今天不超过 [MAX_AUTO_DAYS] 天、且不在未来时才进入「进行中」。 */
    fun canAutoContinue(start: LocalDate, today: LocalDate): Boolean {
        if (start.isAfter(today)) return false
        return ChronoUnit.DAYS.between(start, today) <= MAX_AUTO_DAYS
    }

    /** `[start..endInclusive]` 逐日展开；end 早于 start 时返回空列表。 */
    fun span(start: LocalDate, endInclusive: LocalDate): List<LocalDate> {
        if (endInclusive.isBefore(start)) return emptyList()
        val days = ChronoUnit.DAYS.between(start, endInclusive).toInt()
        return (0..days).map { start.plusDays(it.toLong()) }
    }

    /**
     * 需要补写进库的日期：`[起点..昨天]` 里还没有记录的日子。
     *
     * 幂等——已有记录一律跳过（不覆盖用户填的流量 / 症状 / 日记）。
     * 今天不进这个列表：它由 [span] 派生显示，等用户点「走了」或到了明天再落库。
     */
    fun pendingWrites(
        start: LocalDate,
        recordedDates: Set<LocalDate>,
        today: LocalDate
    ): List<LocalDate> = span(start, today.minusDays(1)).filter { it !in recordedDates }

    /** 收尾：把 `[起点..结束日]` 全部落库（含结束日当天）。 */
    fun closingDates(start: LocalDate, end: LocalDate): List<LocalDate> = span(start, end)
}
