package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.database.model.PoopRecordRelation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 备份文件的编解码与校验。
 *
 * 设计取舍：
 * - 用显式 serializer，不走 reified 扩展，避免不同 Compose/Kotlin 版本下解析入口不一致。
 * - 缺字段一律给默认值，让「校验」而不是「反序列化」来报错，错误信息才可定位。
 * - localDate 与 (instant, zoneId) 的一致性允许 ±1 天误差：时区数据库更新可能改变历史偏移，
 *   完全相等会把合法但年代久远的记录判死。
 */
object BackupCodec {

    const val APP_ID = "LifeTrack"
    const val SCHEMA_VERSION = 1
    const val MAX_EVENTS = 100_000

    private const val BASE_TYPE = "poop"

    private val MIN_TIMESTAMP = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli()
    private val MAX_FUTURE_SKEW_DAYS = 365L

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(
        types: List<TrackTypeEntity>,
        events: List<PoopRecordRelation>,
        now: Instant = Instant.now()
    ): String {
        val file = BackupFileDto(
            app = APP_ID,
            schemaVersion = SCHEMA_VERSION,
            exportedAt = now.toString(),
            eventCount = events.size,
            types = types.map { it.toDto() },
            events = events.map { it.toDto() }
        )
        return json.encodeToString(BackupFileDto.serializer(), file)
    }

    fun decode(text: String, now: Instant = Instant.now()): BackupDecodeResult {
        val file = runCatching { json.decodeFromString(BackupFileDto.serializer(), text) }.getOrNull()
            ?: return BackupDecodeResult.Invalid(BackupError.NOT_JSON)

        if (file.app != APP_ID) return BackupDecodeResult.Invalid(BackupError.NOT_LIFETRACK_FILE)
        if (file.schemaVersion != SCHEMA_VERSION) {
            return BackupDecodeResult.Invalid(BackupError.UNSUPPORTED_VERSION, "v${file.schemaVersion}")
        }
        if (file.events.size > MAX_EVENTS) {
            return BackupDecodeResult.Invalid(BackupError.TOO_MANY_EVENTS, "${file.events.size}")
        }
        if (file.eventCount != file.events.size) {
            return BackupDecodeResult.Invalid(
                BackupError.EVENT_COUNT_MISMATCH,
                "${file.eventCount}/${file.events.size}"
            )
        }

        val types = file.types.map { it.toEntity() }.distinctBy { it.code }
        if (types.isEmpty() || types.any { it.code.isBlank() }) {
            return BackupDecodeResult.Invalid(BackupError.UNKNOWN_TYPE)
        }
        if (types.none { it.code == BASE_TYPE }) {
            return BackupDecodeResult.Invalid(BackupError.MISSING_BASE_TYPE)
        }

        val knownTypes = types.mapTo(HashSet()) { it.code }
        val seenIds = HashSet<String>(file.events.size)
        val events = ArrayList<PoopRecordRelation>(file.events.size)
        val maxTimestamp = now.plusSeconds(MAX_FUTURE_SKEW_DAYS * 24 * 3600).toEpochMilli()

        for ((index, dto) in file.events.withIndex()) {
            val label = dto.id.ifBlank { "#${index + 1}" }
            if (dto.id.isBlank() || !seenIds.add(dto.id)) {
                return BackupDecodeResult.Invalid(BackupError.DUPLICATE_EVENT_ID, label)
            }
            if (dto.typeCode !in knownTypes) {
                return BackupDecodeResult.Invalid(BackupError.UNKNOWN_TYPE, label)
            }
            val zone = runCatching { ZoneId.of(dto.zoneId) }.getOrNull()
                ?: return BackupDecodeResult.Invalid(BackupError.INVALID_ZONE, label)
            val declaredDate = runCatching { LocalDate.parse(dto.localDate) }.getOrNull()
                ?: return BackupDecodeResult.Invalid(BackupError.INVALID_LOCAL_DATE, label)
            if (dto.occurredAtEpochMillis !in MIN_TIMESTAMP..maxTimestamp) {
                return BackupDecodeResult.Invalid(BackupError.INVALID_TIMESTAMP, label)
            }
            val expectedDate = Instant.ofEpochMilli(dto.occurredAtEpochMillis).atZone(zone).toLocalDate()
            if (abs(ChronoUnit.DAYS.between(expectedDate, declaredDate)) > 1) {
                return BackupDecodeResult.Invalid(BackupError.LOCAL_DATE_MISMATCH, label)
            }

            val detail = if (dto.typeCode == BASE_TYPE) dto.poop?.toEntity(dto.id) else null
            if (detail is DetailParse.Failed) {
                return BackupDecodeResult.Invalid(detail.error, label)
            }

            events += PoopRecordRelation(
                event = TrackEventEntity(
                    id = dto.id,
                    typeCode = dto.typeCode,
                    occurredAtEpochMillis = dto.occurredAtEpochMillis,
                    localDate = declaredDate.toString(),
                    zoneId = zone.id,
                    note = dto.note,
                    createdAtEpochMillis = dto.createdAtEpochMillis.takeIf { it > 0 }
                        ?: dto.occurredAtEpochMillis,
                    updatedAtEpochMillis = dto.updatedAtEpochMillis.takeIf { it > 0 }
                        ?: dto.occurredAtEpochMillis
                ),
                detail = (detail as? DetailParse.Parsed)?.entity
            )
        }

        return BackupDecodeResult.Success(BackupPayload(types = types, events = events))
    }

    private sealed interface DetailParse {
        data class Parsed(val entity: PoopDetailEntity) : DetailParse
        data class Failed(val error: BackupError) : DetailParse
    }

    private fun BackupPoopDto.toEntity(eventId: String): DetailParse {
        bristolType?.let { if (it !in 1..7) return DetailParse.Failed(BackupError.INVALID_BRISTOL) }
        amountCode?.let { if (it !in 1..3) return DetailParse.Failed(BackupError.INVALID_AMOUNT) }
        effortCode?.let { if (it !in 1..3) return DetailParse.Failed(BackupError.INVALID_EFFORT) }
        return DetailParse.Parsed(
            PoopDetailEntity(
                eventId = eventId,
                bristolType = bristolType,
                amountCode = amountCode,
                effortCode = effortCode,
                urgency = urgency,
                abdominalDiscomfort = abdominalDiscomfort,
                incompleteEvacuation = incompleteEvacuation
            )
        )
    }

    private fun TrackTypeEntity.toDto() = BackupTypeDto(
        code = code,
        name = name,
        iconKey = iconKey,
        category = category,
        trackingMode = trackingMode,
        enabled = enabled,
        sortOrder = sortOrder,
        schemaVersion = schemaVersion
    )

    private fun BackupTypeDto.toEntity() = TrackTypeEntity(
        code = code.trim(),
        name = name,
        iconKey = iconKey,
        category = category,
        trackingMode = trackingMode,
        enabled = enabled,
        sortOrder = sortOrder,
        schemaVersion = schemaVersion
    )

    private fun PoopRecordRelation.toDto() = BackupEventDto(
        id = event.id,
        typeCode = event.typeCode,
        occurredAtEpochMillis = event.occurredAtEpochMillis,
        localDate = event.localDate,
        zoneId = event.zoneId,
        note = event.note,
        createdAtEpochMillis = event.createdAtEpochMillis,
        updatedAtEpochMillis = event.updatedAtEpochMillis,
        poop = detail?.let {
            BackupPoopDto(
                bristolType = it.bristolType,
                amountCode = it.amountCode,
                effortCode = it.effortCode,
                urgency = it.urgency,
                abdominalDiscomfort = it.abdominalDiscomfort,
                incompleteEvacuation = it.incompleteEvacuation
            )
        }
    )
}

@Serializable
internal data class BackupFileDto(
    val app: String = "",
    val schemaVersion: Int = 0,
    val exportedAt: String = "",
    val eventCount: Int = 0,
    val types: List<BackupTypeDto> = emptyList(),
    val events: List<BackupEventDto> = emptyList()
)

@Serializable
internal data class BackupTypeDto(
    val code: String = "",
    val name: String = "",
    val iconKey: String = "",
    val category: String = "",
    val trackingMode: String = "EVENT",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val schemaVersion: Int = 1
)

@Serializable
internal data class BackupEventDto(
    val id: String = "",
    val typeCode: String = "",
    val occurredAtEpochMillis: Long = 0,
    val localDate: String = "",
    val zoneId: String = "",
    val note: String = "",
    val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
    val poop: BackupPoopDto? = null
)

@Serializable
internal data class BackupPoopDto(
    val bristolType: Int? = null,
    val amountCode: Int? = null,
    val effortCode: Int? = null,
    val urgency: Boolean? = null,
    val abdominalDiscomfort: Boolean? = null,
    val incompleteEvacuation: Boolean? = null
)
