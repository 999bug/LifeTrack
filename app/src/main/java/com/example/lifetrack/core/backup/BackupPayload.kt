package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.database.model.PoopRecordRelation

/** 一份完整快照：类型表 + 全部记录（含详情）。恢复时整体替换。 */
data class BackupPayload(
    val types: List<TrackTypeEntity>,
    val events: List<PoopRecordRelation>
)

/** 解析失败的原因。对外文案由 UI 层映射，这里只保留可判定的枚举。 */
enum class BackupError {
    NOT_JSON,
    NOT_LIFETRACK_FILE,
    UNSUPPORTED_VERSION,
    TOO_MANY_EVENTS,
    EVENT_COUNT_MISMATCH,
    DUPLICATE_EVENT_ID,
    UNKNOWN_TYPE,
    MISSING_BASE_TYPE,
    INVALID_ZONE,
    INVALID_LOCAL_DATE,
    LOCAL_DATE_MISMATCH,
    INVALID_TIMESTAMP,
    INVALID_BRISTOL,
    INVALID_AMOUNT,
    INVALID_EFFORT
}

sealed interface BackupDecodeResult {
    data class Success(val payload: BackupPayload) : BackupDecodeResult

    /** [detail] 用来指出是哪一条记录出问题，便于用户定位被改坏的文件。 */
    data class Invalid(val error: BackupError, val detail: String? = null) : BackupDecodeResult
}
