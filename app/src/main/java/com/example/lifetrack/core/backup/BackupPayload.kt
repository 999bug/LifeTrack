package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord

/** 一份完整快照：类型表 + 全部记录（含各类型可选详情）。恢复时整体替换。 */
data class BackupPayload(
    val types: List<TrackTypeEntity>,
    val events: List<BackupRecord>
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
    INVALID_EFFORT,
    UNSUPPORTED_DETAIL_TYPE,
    DETAIL_TYPE_MISMATCH,
    INVALID_DETAIL_FIELD,
    DETAIL_NOT_ALLOWED,

    /** 口令错误，或密文 / 头部被改动。GCM 认证失败无法区分两者，提示时不要替用户下结论。 */
    WRONG_PASSPHRASE,

    /** 加密容器的结构不合法：Base64 损坏、盐 / IV 长度不对、密文被截断。 */
    INVALID_ENVELOPE,

    /** KDF / 加密算法或迭代次数超出当前版本支持范围。 */
    UNSUPPORTED_ENCRYPTION,

    EMPTY_PASSPHRASE,

    /** 记录过多，加密后的文件会超过 [BackupIo.MAX_BYTES]。 */
    BACKUP_TOO_LARGE
}

sealed interface BackupDecodeResult {
    data class Success(val payload: BackupPayload) : BackupDecodeResult

    /** [detail] 用来指出是哪一条记录出问题，便于用户定位被改坏的文件。 */
    data class Invalid(val error: BackupError, val detail: String? = null) : BackupDecodeResult

    /** v3 加密备份：需要用户输入口令后带口令再解一次。 */
    data object PassphraseRequired : BackupDecodeResult
}
