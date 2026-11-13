package com.example.lifetrack.feature.sport

import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 一次运动的派生视图：只读详情字段，不补算热量 / 配速 / 距离。 */
data class SportSession(
    val id: String,
    val date: LocalDate,
    val sport: String,
    /** 0 表示这条记录的时长缺失或非法，统计时按 0 计而不是猜一个值。 */
    val minutes: Int,
    val intensity: String,
    val note: String,
    val epochMillis: Long,
    val zoneId: String
)

/** 本周里的某一天：节奏格按 [minutes] 决定高度，[label] 固定「一二三四五六日」。 */
data class SportDay(
    val date: LocalDate,
    val label: String,
    val minutes: Int,
    val count: Int,
    val isToday: Boolean,
    val isFuture: Boolean
)

data class SportWeek(
    val start: LocalDate,
    val end: LocalDate,
    val count: Int,
    val totalMinutes: Int,
    /** 只统计本周实际出现过的类型；键可能是已下线的旧类型。 */
    val bySport: Map<String, Int>,
    val days: List<SportDay>
) {
    val isIdle: Boolean get() = count == 0

    /** 界面三类（瑜伽 / 骑行 / 跑步）的分钟数，顺序固定，用于占比条与图例。 */
    val knownMinutes: List<Pair<String, Int>>
        get() = SportFields.SPORTS.mapNotNull { sport ->
            bySport[sport]?.takeIf { it > 0 }?.let { sport to it }
        }

    /** 旧类型合计：占比条里单独一段中性色，不伪装成三类之一。 */
    val legacyMinutes: Int
        get() = bySport.filterKeys { it !in SportFields.SPORTS }.values.sum()
}

data class SportBrief(
    val weekCount: Int,
    val weekMinutes: Int,
    val avgMinutes: Int,
    val monthCount: Int,
    val topSport: String?,
    val topSportCount: Int
)

/**
 * 运动子应用的派生层（界面定稿：节奏型）。
 *
 * 只做「把已有记录重新排列」这一件事：本周次数 / 时长 / 三类占比 / 七天节奏 / 简报。
 * 不推算热量、配速、距离，也不写库——口径与运动卡、统计页一致，
 * 换设备或重装后从记录重算，不需要额外迁移。
 *
 * 所有函数都是纯函数，时间基准由调用方传入 `today`，便于单测固定日期。
 */
object SportStats {

    /** 取数窗口：12 周多一点，够画本周节奏与「近 4 周」简报，又不至于把早年数据全读进内存。 */
    const val WINDOW_DAYS = 90L

    /** 简报里「最常见类型」的统计窗口（含今天）。 */
    private const val BRIEF_WINDOW_DAYS = 28L

    /** 取数窗口起点（含今天共 [WINDOW_DAYS] 天），ViewModel 与界面共用同一口径。 */
    fun windowStart(today: LocalDate): LocalDate = today.minusDays(WINDOW_DAYS - 1)

    /** 周节奏列宽度：周一在前，与日历习惯一致。 */
    private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 记录 → 派生视图，按时间倒序；日期解析失败或不是运动类型的记录直接跳过。 */
    fun sessions(records: List<BackupRecord>): List<SportSession> = records
        .asSequence()
        .filter { it.event.typeCode == SportFields.TYPE_CODE }
        .mapNotNull { record ->
            val date = runCatching { LocalDate.parse(record.event.localDate) }.getOrNull()
                ?: return@mapNotNull null
            val fields = (record.detail as? RecordDetail.Generic)?.fields.orEmpty()
            SportSession(
                id = record.event.id,
                date = date,
                sport = fields[SportFields.SPORT].orEmpty(),
                minutes = fields[SportFields.DURATION]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                intensity = fields[SportFields.INTENSITY].orEmpty(),
                note = record.event.note,
                epochMillis = record.event.occurredAtEpochMillis,
                zoneId = record.event.zoneId
            )
        }
        .sortedWith(compareByDescending<SportSession> { it.epochMillis }.thenByDescending { it.date })
        .toList()

    /** 本周（周一起算）的概览：次数、总时长、按类型分钟数、七天节奏。 */
    fun week(sessions: List<SportSession>, today: LocalDate): SportWeek {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(6)
        val inWeek = sessions.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        val bySport = mutableMapOf<String, Int>()
        inWeek.forEach { session ->
            if (session.sport.isNotEmpty()) {
                bySport[session.sport] = (bySport[session.sport] ?: 0) + session.minutes
            }
        }
        val ordered = linkedMapOf<String, Int>()
        SportFields.SPORTS.forEach { sport -> bySport[sport]?.let { ordered[sport] = it } }
        bySport.filterKeys { it !in SportFields.SPORTS }.forEach { (sport, minutes) ->
            ordered[sport] = minutes
        }
        val days = WEEKDAY_LABELS.mapIndexed { index, label ->
            val date = start.plusDays(index.toLong())
            val sameDay = inWeek.filter { it.date == date }
            SportDay(
                date = date,
                label = label,
                minutes = sameDay.sumOf { it.minutes },
                count = sameDay.size,
                isToday = date == today,
                isFuture = date.isAfter(today)
            )
        }
        return SportWeek(
            start = start,
            end = end,
            count = inWeek.size,
            totalMinutes = inWeek.sumOf { it.minutes },
            bySport = ordered,
            days = days
        )
    }

    /** 某个类型的最近一次，用于快记卡上的「上次 45 分钟」。 */
    fun lastOf(sessions: List<SportSession>, sport: String): SportSession? =
        sessions.firstOrNull { it.sport == sport }

    /**
     * 简报四个指标：本周次数与时长、平均每次、近 4 周次数、最常见类型。
     * 没有数据时返回 0 / 0 / 0 / null，由界面显示「—」，不编造数字。
     */
    fun brief(sessions: List<SportSession>, today: LocalDate): SportBrief {
        val week = week(sessions, today)
        val windowStart = today.minusDays(BRIEF_WINDOW_DAYS - 1)
        val recent = sessions.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(today) }
        val top = recent
            .filter { it.sport.isNotEmpty() }
            .groupingBy { it.sport }
            .eachCount()
            .maxByOrNull { it.value }
        return SportBrief(
            weekCount = week.count,
            weekMinutes = week.totalMinutes,
            avgMinutes = if (week.count > 0) week.totalMinutes / week.count else 0,
            monthCount = recent.size,
            topSport = top?.key,
            topSportCount = top?.value ?: 0
        )
    }

    /** 距上次运动多久：今天 / 昨天 / N 天前 / 还没记录。 */
    fun gapLabel(lastDate: LocalDate?, today: LocalDate): String {
        if (lastDate == null) return "还没有记录"
        val days = ChronoUnit.DAYS.between(lastDate, today).toInt()
        return when {
            days <= 0 -> "今天动过"
            days == 1 -> "昨天动过"
            else -> "距上次 $days 天"
        }
    }
}
