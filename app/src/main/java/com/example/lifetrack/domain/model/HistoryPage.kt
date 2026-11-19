package com.example.lifetrack.domain.model

import com.example.lifetrack.core.model.BackupRecord

/** (occurredAtEpochMillis, id) 复合游标，保证同毫秒记录不会漏页或重复。 */
data class RecordCursor(
    val occurredAtEpochMillis: Long,
    val id: String
)

/** 固定页大小的 keyset 查询结果；[hasMore] 来自 limit + 1 探测。 */
data class HistoryPage(
    val records: List<BackupRecord>,
    val nextCursor: RecordCursor?,
    val hasMore: Boolean
)
