package com.example.lifetrack.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 一个经期窗口（实际或预测），[endInclusive] 含当天。 */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate
) {
    val lengthDays: Int get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)
}

/** 连续有记录日归并出的一段经期；孤立单日也是一段（[lengthDays] = 1）。 */
data class CycleSegment(
    val start: LocalDate,
    val end: LocalDate,
    val lengthDays: Int
)

/**
 * 今天所处的周期阶段。只描述「记录参考」，不产出排卵 / 易孕 / 安全期等结论。
 * [InPeriod.dayIndex] 从 1 起；[BeforeNext.days] / [Delayed.days] 均为非负天数。
 */
sealed interface Phase {
    data class InPeriod(val dayIndex: Int) : Phase
    data class BeforeNext(val days: Int) : Phase
    data class Delayed(val days: Int) : Phase
    data object Insufficient : Phase
}

/** 一次派生计算的全部结果，不落库；记录变了就重算，避免「记录变、预测没变」。 */
data class CycleSnapshot(
    val segments: List<CycleSegment>,
    /** 相邻段首日之差（天），保留全部样本供趋势展示，异常值只在算中位数时剔除。 */
    val cycleLengths: List<Int>,
    val medianCycleDays: Int?,
    val medianPeriodDays: Int?,
    val currentPhase: Phase,
    val predictedPeriods: List<DateRange>,
    val sampleReliable: Boolean
)

/**
 * 纯 JVM 周期推算（设计 §5.1）：只用 `LocalDate`，不涉时刻与时区，天然避开跨时区复杂度，
 * 可被 `testDebugUnitTest` 直接覆盖。所有派生数据都不持久化。
 *
 * 口径：连续有记录日归并成段；周期长度取相邻段首日之差，落在 [CYCLE_RANGE] 外的异常样本
 * 不参与中位数但段本身保留；有效样本 ≥ [MIN_RELIABLE_SAMPLES] 才判可靠并预测，否则回退到
 * 用户配置值且不给预测窗口。只预测经期，不做排卵日 / 易孕期 / 安全期，不宣称准确率。
 */
object CycleCalculator {
    val CYCLE_RANGE = 15..60
    const val DEFAULT_SAMPLE_SIZE = 6
    const val MIN_RELIABLE_SAMPLES = 3
    const val MAX_PREDICTED_CYCLES = 3

    fun calculate(
        recordedDates: Set<LocalDate>,
        today: LocalDate,
        configuredCycleDays: Int,
        configuredPeriodDays: Int,
        sampleSize: Int = DEFAULT_SAMPLE_SIZE
    ): CycleSnapshot {
        val segments = segmentsOf(recordedDates, today)
        val cycleLengths = segments.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a.start, b.start).toInt()
        }
        val validCycleLengths = cycleLengths.filter { it in CYCLE_RANGE }
        val sampleReliable = validCycleLengths.size >= MIN_RELIABLE_SAMPLES

        val medianCycleDays = median(validCycleLengths.takeLast(sampleSize))
        val medianPeriodDays = median(segments.map { it.lengthDays }.takeLast(sampleSize))

        val effectiveCycleDays =
            if (sampleReliable) medianCycleDays ?: configuredCycleDays else configuredCycleDays
        val effectivePeriodDays =
            if (sampleReliable) medianPeriodDays ?: configuredPeriodDays else configuredPeriodDays

        val lastStart = segments.lastOrNull()?.start
        val predictedPeriods = if (sampleReliable && lastStart != null) {
            predictWindows(lastStart, effectiveCycleDays, effectivePeriodDays)
        } else {
            emptyList()
        }

        val phase = phaseOf(segments, today, sampleReliable, lastStart, effectiveCycleDays)

        return CycleSnapshot(
            segments = segments,
            cycleLengths = cycleLengths,
            medianCycleDays = medianCycleDays,
            medianPeriodDays = medianPeriodDays,
            currentPhase = phase,
            predictedPeriods = predictedPeriods,
            sampleReliable = sampleReliable
        )
    }

    /** 只归并经期段（与配置值无关），供补齐计划等只需要段信息的调用方复用。 */
    fun segmentsOf(recordedDates: Set<LocalDate>, today: LocalDate): List<CycleSegment> =
        mergeSegments(recordedDates.filter { !it.isAfter(today) }.sorted())

    private fun mergeSegments(dates: List<LocalDate>): List<CycleSegment> {
        if (dates.isEmpty()) return emptyList()
        val segments = mutableListOf<CycleSegment>()
        var start = dates.first()
        var prev = dates.first()
        for (index in 1 until dates.size) {
            val date = dates[index]
            if (ChronoUnit.DAYS.between(prev, date) == 1L) {
                prev = date
            } else {
                segments.add(segment(start, prev))
                start = date
                prev = date
            }
        }
        segments.add(segment(start, prev))
        return segments
    }

    private fun segment(start: LocalDate, end: LocalDate): CycleSegment =
        CycleSegment(start, end, (ChronoUnit.DAYS.between(start, end) + 1).toInt())

    private fun predictWindows(
        lastStart: LocalDate,
        cycleDays: Int,
        periodDays: Int
    ): List<DateRange> {
        val windows = mutableListOf<DateRange>()
        var next = lastStart.plusDays(cycleDays.toLong())
        repeat(MAX_PREDICTED_CYCLES) {
            windows.add(DateRange(next, next.plusDays((periodDays - 1).toLong())))
            next = next.plusDays(cycleDays.toLong())
        }
        return windows
    }

    private fun phaseOf(
        segments: List<CycleSegment>,
        today: LocalDate,
        sampleReliable: Boolean,
        lastStart: LocalDate?,
        effectiveCycleDays: Int
    ): Phase {
        val containing = segments.firstOrNull { today in DateRange(it.start, it.end) }
        if (containing != null) {
            return Phase.InPeriod((ChronoUnit.DAYS.between(containing.start, today) + 1).toInt())
        }
        if (!sampleReliable || lastStart == null) return Phase.Insufficient
        val nextStart = lastStart.plusDays(effectiveCycleDays.toLong())
        val days = ChronoUnit.DAYS.between(today, nextStart).toInt()
        return if (days < 0) Phase.Delayed(-days) else Phase.BeforeNext(days)
    }

    /** 偶数个取中间两数均值向下取整；空集返回 null（无样本可算）。 */
    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}

/**
 * 「大姨妈走了」的补齐计划（设计 §5.2），纯 JVM、不写库，便于单测覆盖间隔上限 / 幂等 / 未来日期。
 *
 * 从「最近一段经期首日」到选中日逐天补齐缺失日期；已有记录日一律跳过（不覆盖详情），
 * 未来日期不补。间隔超过 [MAX_GAP_DAYS] 天返回 [Plan.TooFar]，由 UI 要求逐日确认，防止误触批量写入。
 */
object PeriodFill {
    const val MAX_GAP_DAYS = 14

    sealed interface Plan {
        data object TooFar : Plan
        data class Fill(val dates: List<LocalDate>) : Plan
    }

    fun plan(
        recordedDates: Set<LocalDate>,
        end: LocalDate,
        today: LocalDate,
        maxGap: Int = MAX_GAP_DAYS
    ): Plan {
        val segments = CycleCalculator.segmentsOf(recordedDates, today)
        val segStart = segments.lastOrNull { !it.start.isAfter(end) }?.start ?: end
        if (ChronoUnit.DAYS.between(segStart, end) > maxGap) return Plan.TooFar
        val dates = buildList {
            var cursor = segStart
            while (!cursor.isAfter(end)) {
                if (!cursor.isAfter(today) && cursor !in recordedDates) add(cursor)
                cursor = cursor.plusDays(1)
            }
        }
        return Plan.Fill(dates)
    }
}