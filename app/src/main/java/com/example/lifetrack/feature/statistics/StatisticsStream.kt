package com.example.lifetrack.feature.statistics

import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.statsWindow
import com.example.lifetrack.domain.repository.TrackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * 统计页的数据流：把「当前日期 + 各周期偏移 + 类型筛选」解析成四个周期窗口，
 * 合并三路查询（按天计数 / 时刻 / 详情）后聚合出每个周期的 [PeriodStats]。
 *
 * 从 MainViewModel 里提出来单独成函数，是为了能脱离 ViewModel 直接验窗口与聚合口径；
 * 调度（flowOn）与状态收口（stateIn）仍由调用方决定。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeStatistics(
    repository: TrackRepository,
    today: Flow<LocalDate>,
    offsets: Flow<Map<StatsPeriod, Int>>,
    typeFilter: Flow<String?>
): Flow<Map<StatsPeriod, PeriodStats>> =
    combine(today, offsets, typeFilter) { day, offsetsByPeriod, typeCode ->
        Triple(day, offsetsByPeriod, typeCode)
    }.flatMapLatest { (day, offsetsByPeriod, typeCode) ->
        val windows = StatsPeriod.entries.associateWith { period ->
            statsWindow(period, offsetsByPeriod[period] ?: 0, day)
        }
        val rangeStart = windows.values.minOf { it.previous.start }.toString()
        val rangeEnd = windows.values.maxOf { it.lastDate }.toString()
        // 形态分布只在排便口径下查，避免与其他类型的总数串口径。
        val details = if (typeCode == DEFAULT_POOP_TYPE.code) {
            repository.observeStatDetails(rangeStart, rangeEnd)
        } else {
            flowOf(emptyList())
        }
        combine(
            repository.observeDailyCounts(rangeStart, rangeEnd, typeCode),
            repository.observeEventTimestamps(rangeStart, rangeEnd, typeCode),
            details
        ) { dailyCounts, timestamps, details ->
            windows.mapValues { (_, window) ->
                StatsAggregator.build(
                    window = window,
                    today = day,
                    dailyCounts = dailyCounts,
                    timestamps = timestamps,
                    details = details
                )
            }
        }
    }

/** 首帧占位：与真实聚合同口径（0 条记录），避免首帧出现 null 分支。 */
internal fun emptyStatistics(today: LocalDate): Map<StatsPeriod, PeriodStats> =
    StatsPeriod.entries.associateWith { period ->
        StatsAggregator.build(statsWindow(period, 0, today), today, emptyList(), emptyList())
    }
