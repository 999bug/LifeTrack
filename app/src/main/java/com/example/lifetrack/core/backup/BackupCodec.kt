package com.example.lifetrack.core.backup

import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.security.BackupCrypto
import com.example.lifetrack.core.security.PassphraseQuality
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.math.abs

/**
 * 备份文件的编解码与校验。
 *
 * 设计取舍：
 * - 用显式 serializer，不走 reified 扩展，避免不同 Compose/Kotlin 版本下解析入口不一致。
 * - 缺字段一律给默认值，让「校验」而不是「反序列化」来报错，错误信息才可定位。
 * - localDate 与 (instant, zoneId) 的一致性允许 ±1 天误差：时区数据库更新可能改变历史偏移，
 *   完全相等会把合法但年代久远的记录判死。
 * - v1 / v2 共用一个 DTO：v1 的 `poop` 字段在 v2 里原样保留，只新增通用 `detail` 通道，
 *   因此旧备份无需第二套解析器；校验按版本号收紧，避免旧格式被塞入无法落库的数据。
 * - v3 只是加密外壳：正文仍是完整的 v2 JSON，解密后走同一套校验，
 *   不维护第二套业务 DTO；v1 / v2 明文的读取分支永久保留。
 */
object BackupCodec {

    const val APP_ID = "LifeTrack"
    const val LEGACY_SCHEMA_VERSION = 1
    const val SCHEMA_VERSION = 2
    val SUPPORTED_VERSIONS = setOf(LEGACY_SCHEMA_VERSION, SCHEMA_VERSION)

    /** v3 是加密容器：外层头部明文 + 内部仍是 v2 明文 JSON，因此恢复链路不需要第二套 DTO。 */
    const val ENCRYPTED_SCHEMA_VERSION = 3
    const val MAX_EVENTS = 100_000

    /** 导出 UI 的口令下限。core 只拒绝空口令，避免把外部生成的短口令备份判死。 */
    const val MIN_PASSPHRASE_LENGTH = PassphraseQuality.MIN_LENGTH

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
        events: List<BackupRecord>,
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

    /**
     * v3 导出：先按 v2 序列化正文，再整体加密。正文仍是标准备份 JSON，
     * 因此加密文件的「内容」与明文备份同构，解密后走同一套校验。
     *
     * 口令只在本方法内使用，不缓存、不写文件；失败时抛 [BackupCrypto.Failure]。
     */
    fun encodeEncrypted(
        types: List<TrackTypeEntity>,
        events: List<BackupRecord>,
        passphrase: CharArray,
        now: Instant = Instant.now()
    ): String {
        val plaintext = encode(types, events, now).toByteArray(StandardCharsets.UTF_8)
        val salt = BackupCrypto.newSalt()
        val iv = BackupCrypto.newIv()
        val saltText = encode64(salt)
        val ivText = encode64(iv)
        val exportedAt = now.toString()
        val aad = BackupEnvelope.aad(
            app = APP_ID,
            schemaVersion = ENCRYPTED_SCHEMA_VERSION,
            exportedAt = exportedAt,
            kdf = BackupCrypto.KDF_ALGORITHM,
            iterations = BackupCrypto.DEFAULT_ITERATIONS,
            salt = saltText,
            iv = ivText,
            cipher = BackupCrypto.CIPHER_NAME
        )
        val ciphertext = try {
            BackupCrypto.seal(
                plaintext = plaintext,
                passphrase = passphrase,
                salt = salt,
                iv = iv,
                iterations = BackupCrypto.DEFAULT_ITERATIONS,
                aad = aad
            )
        } finally {
            plaintext.fill(0)
        }

        val file = EncryptedBackupFileDto(
            app = APP_ID,
            schemaVersion = ENCRYPTED_SCHEMA_VERSION,
            exportedAt = exportedAt,
            kdf = BackupCrypto.KDF_ALGORITHM,
            iterations = BackupCrypto.DEFAULT_ITERATIONS,
            salt = saltText,
            iv = ivText,
            cipher = BackupCrypto.CIPHER_NAME,
            payload = encode64(ciphertext)
        )
        val text = json.encodeToString(EncryptedBackupFileDto.serializer(), file)
        // 与导入端共用同一个上限：绝不让本应用导出自己读不了的文件。
        if (text.toByteArray(StandardCharsets.UTF_8).size > BackupIo.MAX_BYTES) {
            throw BackupCrypto.Failure(BackupCrypto.Reason.PLAINTEXT_TOO_LARGE)
        }
        return text
    }

    /**
     * 兼容入口：v1 / v2 明文备份直接解析；v3 加密备份需要 [passphrase]。
     * 未给口令时返回 [BackupDecodeResult.PassphraseRequired]，由 UI 弹口令输入。
     */
    fun decode(
        text: String,
        now: Instant = Instant.now(),
        options: BackupDecodeOptions = BackupDecodeOptions.Default,
        passphrase: CharArray? = null
    ): BackupDecodeResult {
        val envelope = parseEnvelope(text)
        if (envelope != null) {
            if (envelope.app != APP_ID) {
                return BackupDecodeResult.Invalid(BackupError.NOT_LIFETRACK_FILE)
            }
            // v3 只可能是加密容器：缺 payload 属于文件损坏，不是「版本不支持」。
            if (envelope.payload.isBlank()) {
                return BackupDecodeResult.Invalid(BackupError.INVALID_ENVELOPE, "payload")
            }
            return decodeEncrypted(envelope, passphrase, now, options)
        }
        return decodePlaintext(text, now, options)
    }

    /**
     * 口令轮换：用旧口令解出完整payload，再用新口令重新加密。
     *
     * - 只接受 v3 加密备份；明文文件没有口令可换。
     * - 重新编码会换新的盐与 IV，并重新走一遍字段校验；不会把旧密文原样复制。
     * - 生成后会立刻用新口令解一遍并比对，确认条数与字段一致才返回，避免交付一个打不开的文件。
     * - 不读写文件、不删原文件：落盘与清理交给调用方，核心层保持可单测。
     */
    fun reencrypt(
        text: String,
        oldPassphrase: CharArray,
        newPassphrase: CharArray,
        now: Instant = Instant.now()
    ): BackupReencryptResult {
        val envelope = parseEnvelope(text)
            ?: return BackupReencryptResult.Invalid(BackupError.NOT_ENCRYPTED_BACKUP)
        if (envelope.app != APP_ID) {
            return BackupReencryptResult.Invalid(BackupError.NOT_LIFETRACK_FILE)
        }
        if (envelope.payload.isBlank()) {
            return BackupReencryptResult.Invalid(BackupError.INVALID_ENVELOPE, "payload")
        }

        return when (val decoded = decodeEncrypted(envelope, oldPassphrase, now, BackupDecodeOptions.Default)) {
            is BackupDecodeResult.Success -> {
                val payload = decoded.payload
                val reencrypted = try {
                    encodeEncrypted(payload.types, payload.events, newPassphrase, now)
                } catch (e: BackupCrypto.Failure) {
                    return BackupReencryptResult.Invalid(e.reason.toBackupError())
                }
                val checkEnvelope = parseEnvelope(reencrypted)
                    ?: return BackupReencryptResult.Invalid(BackupError.INVALID_ENVELOPE, "verify")
                val check = decodeEncrypted(checkEnvelope, newPassphrase, now, BackupDecodeOptions.Default)
                if (check !is BackupDecodeResult.Success || check.payload != payload) {
                    return BackupReencryptResult.Invalid(BackupError.INVALID_ENVELOPE, "verify")
                }
                BackupReencryptResult.Success(reencrypted)
            }

            is BackupDecodeResult.Invalid -> BackupReencryptResult.Invalid(decoded.error, decoded.detail)
            BackupDecodeResult.PassphraseRequired ->
                BackupReencryptResult.Invalid(BackupError.EMPTY_PASSPHRASE)
        }
    }

    /** 只认声明为 v3 的容器；其它版本仍按明文分支处理，保证旧文件走原错误提示。 */
    private fun parseEnvelope(text: String): EncryptedBackupFileDto? =
        runCatching { json.decodeFromString(EncryptedBackupFileDto.serializer(), text) }
            .getOrNull()
            ?.takeIf { it.schemaVersion == ENCRYPTED_SCHEMA_VERSION }

    private fun decodeEncrypted(
        envelope: EncryptedBackupFileDto,
        passphrase: CharArray?,
        now: Instant,
        options: BackupDecodeOptions
    ): BackupDecodeResult {
        if (envelope.app != APP_ID) {
            return BackupDecodeResult.Invalid(BackupError.NOT_LIFETRACK_FILE)
        }
        if (passphrase == null) return BackupDecodeResult.PassphraseRequired
        if (envelope.kdf != BackupCrypto.KDF_ALGORITHM || envelope.cipher != BackupCrypto.CIPHER_NAME) {
            return BackupDecodeResult.Invalid(
                BackupError.UNSUPPORTED_ENCRYPTION,
                "${envelope.kdf.take(32)}/${envelope.cipher.take(32)}"
            )
        }
        if (envelope.iterations !in BackupCrypto.MIN_ITERATIONS..BackupCrypto.MAX_ITERATIONS) {
            return BackupDecodeResult.Invalid(
                BackupError.UNSUPPORTED_ENCRYPTION,
                "iterations=${envelope.iterations}"
            )
        }

        val salt = decode64(envelope.salt)
            ?: return BackupDecodeResult.Invalid(BackupError.INVALID_ENVELOPE, "salt")
        val iv = decode64(envelope.iv)
            ?: return BackupDecodeResult.Invalid(BackupError.INVALID_ENVELOPE, "iv")
        val ciphertext = decode64(envelope.payload)
            ?: return BackupDecodeResult.Invalid(BackupError.INVALID_ENVELOPE, "payload")

        val aad = BackupEnvelope.aad(
            app = envelope.app,
            schemaVersion = envelope.schemaVersion,
            exportedAt = envelope.exportedAt,
            kdf = envelope.kdf,
            iterations = envelope.iterations,
            salt = envelope.salt,
            iv = envelope.iv,
            cipher = envelope.cipher
        )
        val plaintext = try {
            BackupCrypto.open(
                ciphertext = ciphertext,
                passphrase = passphrase,
                salt = salt,
                iv = iv,
                iterations = envelope.iterations,
                aad = aad
            )
        } catch (e: BackupCrypto.Failure) {
            return BackupDecodeResult.Invalid(e.reason.toBackupError())
        }

        return try {
            // 解密后的正文只允许 v1 / v2：嵌套 v3 一律按版本不支持拒绝，避免递归。
            decodePlaintext(String(plaintext, StandardCharsets.UTF_8), now, options)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun BackupCrypto.Reason.toBackupError(): BackupError = when (this) {
        BackupCrypto.Reason.EMPTY_PASSPHRASE -> BackupError.EMPTY_PASSPHRASE
        BackupCrypto.Reason.INVALID_SALT,
        BackupCrypto.Reason.INVALID_IV,
        BackupCrypto.Reason.CIPHERTEXT_TOO_SHORT -> BackupError.INVALID_ENVELOPE
        BackupCrypto.Reason.INVALID_ITERATIONS,
        BackupCrypto.Reason.UNAVAILABLE -> BackupError.UNSUPPORTED_ENCRYPTION
        BackupCrypto.Reason.PLAINTEXT_TOO_LARGE -> BackupError.BACKUP_TOO_LARGE
        BackupCrypto.Reason.DECRYPT_FAILED -> BackupError.WRONG_PASSPHRASE
    }

    private fun decodePlaintext(
        text: String,
        now: Instant,
        options: BackupDecodeOptions
    ): BackupDecodeResult {
        val file = runCatching { json.decodeFromString(BackupFileDto.serializer(), text) }.getOrNull()
            ?: return BackupDecodeResult.Invalid(BackupError.NOT_JSON)

        if (file.app != APP_ID) return BackupDecodeResult.Invalid(BackupError.NOT_LIFETRACK_FILE)
        if (file.schemaVersion !in SUPPORTED_VERSIONS) {
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
        val events = ArrayList<BackupRecord>(file.events.size)
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

            val detail = parseDetail(dto, file.schemaVersion, options)
            if (detail is DetailParse.Failed) {
                return BackupDecodeResult.Invalid(detail.error, label)
            }

            events += BackupRecord(
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
                detail = (detail as? DetailParse.Parsed)?.value
            )
        }

        return BackupDecodeResult.Success(BackupPayload(types = types, events = events))
    }

    /**
     * 详情按类型分派：排便走强类型 `poop` 字段，其余走通用 `detail`。
     * 生产默认不声明任何受支持的通用类型，遇到即拒绝，避免恢复事务静默丢弃数据。
     */
    private fun parseDetail(
        dto: BackupEventDto,
        version: Int,
        options: BackupDecodeOptions
    ): DetailParse {
        if (version == LEGACY_SCHEMA_VERSION && dto.detail != null) {
            return DetailParse.Failed(BackupError.DETAIL_NOT_ALLOWED)
        }
        if (dto.typeCode != BASE_TYPE && dto.poop != null) {
            return DetailParse.Failed(BackupError.DETAIL_NOT_ALLOWED)
        }

        val poopDetail = if (dto.typeCode == BASE_TYPE) dto.poop?.toEntity(dto.id) else null
        if (poopDetail is DetailParse.Failed) return poopDetail
        val poop = (poopDetail as? DetailParse.Parsed)?.value as? RecordDetail.Poop

        val genericDto = dto.detail
        if (genericDto == null) return DetailParse.Parsed(poop)
        if (dto.typeCode == BASE_TYPE) return DetailParse.Failed(BackupError.DETAIL_NOT_ALLOWED)
        if (genericDto.typeCode != dto.typeCode) {
            return DetailParse.Failed(BackupError.DETAIL_TYPE_MISMATCH)
        }
        if (genericDto.typeCode !in options.supportedDetailTypes) {
            return DetailParse.Failed(BackupError.UNSUPPORTED_DETAIL_TYPE)
        }
        if (!DetailLimits.isStructuralValid(genericDto.fields)) {
            return DetailParse.Failed(BackupError.INVALID_DETAIL_FIELD)
        }
        // 没有字段规格就不放行：宁可明确拒绝，也不让未经校验的字段落库。
        val spec = DetailSpecRegistry.specFor(genericDto.typeCode, options.detailSpecs)
            ?: return DetailParse.Failed(BackupError.INVALID_DETAIL_FIELD)
        if (!spec.isFieldsValid(genericDto.fields)) {
            return DetailParse.Failed(BackupError.INVALID_DETAIL_FIELD)
        }
        return DetailParse.Parsed(RecordDetail.Generic(genericDto.typeCode, genericDto.fields))
    }

    private sealed interface DetailParse {
        data class Parsed(val value: RecordDetail?) : DetailParse
        data class Failed(val error: BackupError) : DetailParse
    }

    private fun BackupPoopDto.toEntity(eventId: String): DetailParse {
        bristolType?.let { if (it !in 1..7) return DetailParse.Failed(BackupError.INVALID_BRISTOL) }
        amountCode?.let { if (it !in 1..3) return DetailParse.Failed(BackupError.INVALID_AMOUNT) }
        effortCode?.let { if (it !in 1..3) return DetailParse.Failed(BackupError.INVALID_EFFORT) }
        return DetailParse.Parsed(
            RecordDetail.Poop(
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

    private fun BackupRecord.toDto() = BackupEventDto(
        id = event.id,
        typeCode = event.typeCode,
        occurredAtEpochMillis = event.occurredAtEpochMillis,
        localDate = event.localDate,
        zoneId = event.zoneId,
        note = event.note,
        createdAtEpochMillis = event.createdAtEpochMillis,
        updatedAtEpochMillis = event.updatedAtEpochMillis,
        poop = (detail as? RecordDetail.Poop)?.let {
            BackupPoopDto(
                bristolType = it.bristolType,
                amountCode = it.amountCode,
                effortCode = it.effortCode,
                urgency = it.urgency,
                abdominalDiscomfort = it.abdominalDiscomfort,
                incompleteEvacuation = it.incompleteEvacuation
            )
        },
        detail = (detail as? RecordDetail.Generic)?.let {
            BackupDetailDto(typeCode = it.typeCode, fields = it.fields)
        }
    )

    private fun encode64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode64(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
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
    /** 排便详情，v1 起就存在，v2 原样保留。 */
    val poop: BackupPoopDto? = null,
    /** v2 新增：其余类型的通用详情，值统一字符串，由规格表校验。 */
    val detail: BackupDetailDto? = null
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

@Serializable
internal data class BackupDetailDto(
    val typeCode: String = "",
    val fields: Map<String, String> = emptyMap()
)
