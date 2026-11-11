package com.example.lifetrack.feature.history

import com.example.lifetrack.core.database.model.DailyTypeCountProjection

/**
 * 月历格子的「当天记了什么」摘要。
 *
 * 输入是 DAO 按 (localDate, firstAt) 升序返回的行；这里只做分组与去重，
 * 不重新排序——顺序就是用户当天第一次记录某类型的先后，重组时不会跳位。
 */
object DayCalendarData {

    /** 一格最多画几个类型图标，超出的由界面截断。 */
    const val MAX_MARKS = 3

    /**
     * 折叠成「日期 → 当天出现过的类型码（去重、按首次出现排序）」。
     * 同一天同一类型的多条记录只保留一份，因为格子表达的是「记了哪几类」而不是「各几次」。
     */
    fun typesByDate(rows: List<DailyTypeCountProjection>): Map<String, List<String>> =
        rows.groupBy(keySelector = { it.localDate }, valueTransform = { it.typeCode })
            .mapValues { (_, codes) -> codes.distinct() }

    /** 界面用：把类型列表截到 [MAX_MARKS] 个。 */
    fun visibleMarks(types: List<String>): List<String> = types.take(MAX_MARKS)
}
