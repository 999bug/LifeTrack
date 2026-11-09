package com.example.lifetrack.feature.statistics

import com.example.lifetrack.core.database.model.StatDetailProjection

/**
 * 形态分布。每个字段独立用自己的「已填写条数」作分母：
 * 未填写（NULL）不进任何分箱，也不算作 0 型；非法值整条忽略，不让脏数据把图撑歪。
 *
 * [totalRecords] 是本周期的记录总数，用于卡面展示「已填写 N / 共 M 条」。
 * 它与各 filled 值不相等是正常情况，必须在 UI 上显式说明。
 */
data class DetailDistribution(
    val totalRecords: Int,
    val bristolCounts: List<Int>,
    val amountCounts: List<Int>,
    val effortCounts: List<Int>,
    val urgencyCount: Int,
    val abdominalCount: Int,
    val incompleteCount: Int,
    val filledBristol: Int,
    val filledAmount: Int,
    val filledEffort: Int,
    val filledUrgency: Int,
    val filledAbdominal: Int,
    val filledIncomplete: Int
) {
    val anyFilled: Boolean
        get() = filledBristol + filledAmount + filledEffort +
            filledUrgency + filledAbdominal + filledIncomplete > 0

    companion object {
        fun from(details: List<StatDetailProjection>, totalRecords: Int): DetailDistribution {
            val bristol = IntArray(7)
            val amount = IntArray(3)
            val effort = IntArray(3)
            var filledBristol = 0
            var filledAmount = 0
            var filledEffort = 0
            var urgencyCount = 0
            var abdominalCount = 0
            var incompleteCount = 0
            var filledUrgency = 0
            var filledAbdominal = 0
            var filledIncomplete = 0

            for (row in details) {
                row.bristolType?.let { value ->
                    if (value in 1..7) {
                        bristol[value - 1] += 1
                        filledBristol += 1
                    }
                }
                row.amountCode?.let { value ->
                    if (value in 1..3) {
                        amount[value - 1] += 1
                        filledAmount += 1
                    }
                }
                row.effortCode?.let { value ->
                    if (value in 1..3) {
                        effort[value - 1] += 1
                        filledEffort += 1
                    }
                }
                row.urgency?.let { value ->
                    if (value == 0 || value == 1) {
                        filledUrgency += 1
                        if (value == 1) urgencyCount += 1
                    }
                }
                row.abdominalDiscomfort?.let { value ->
                    if (value == 0 || value == 1) {
                        filledAbdominal += 1
                        if (value == 1) abdominalCount += 1
                    }
                }
                row.incompleteEvacuation?.let { value ->
                    if (value == 0 || value == 1) {
                        filledIncomplete += 1
                        if (value == 1) incompleteCount += 1
                    }
                }
            }

            return DetailDistribution(
                totalRecords = totalRecords,
                bristolCounts = bristol.toList(),
                amountCounts = amount.toList(),
                effortCounts = effort.toList(),
                urgencyCount = urgencyCount,
                abdominalCount = abdominalCount,
                incompleteCount = incompleteCount,
                filledBristol = filledBristol,
                filledAmount = filledAmount,
                filledEffort = filledEffort,
                filledUrgency = filledUrgency,
                filledAbdominal = filledAbdominal,
                filledIncomplete = filledIncomplete
            )
        }
    }
}
