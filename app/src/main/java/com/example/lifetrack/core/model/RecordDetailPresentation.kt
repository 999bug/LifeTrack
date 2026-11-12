package com.example.lifetrack.core.model

import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.DEFAULT_TYPES
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 详情弹窗里的一行：标签 + 值；[muted] 表示「未记录 / 未填写」这类占位值。 */
data class DetailField(
    val label: String,
    val value: String,
    val muted: Boolean = false
)

/** 详情弹窗里的一段分组。 */
data class DetailSection(
    val title: String,
    val fields: List<DetailField>
)

/** 一条记录展开后的只读详情，UI 层只负责渲染，不再自己拼字段与文案。 */
data class RecordDetailView(
    val title: String,
    val sections: List<DetailSection>
)

private val fullTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINA)
private val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val metaTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

private const val NOT_RECORDED = "未记录"
private const val UNKNOWN_TIME = "时间未知"

// 与排便列表卡片的摘要文案保持一致；编辑器里的「轻微 / 明显」是同一枚举的短标签。
private val amountLabels = mapOf(1 to "少量", 2 to "适中", 3 to "大量")
private val effortLabels = mapOf(1 to "不费力", 2 to "轻微费力", 3 to "明显费力")

private fun formatAt(epochMillis: Long, zoneId: String, formatter: DateTimeFormatter): String =
    runCatching {
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(zoneId)).format(formatter)
    }.getOrDefault(UNKNOWN_TIME)

private fun field(label: String, value: String?): DetailField =
    DetailField(label, value ?: NOT_RECORDED, muted = value == null)

private fun typeLabel(typeCode: String): String =
    DEFAULT_TYPES.firstOrNull { it.code == typeCode }?.name ?: typeCode

/**
 * 把一条记录展开成详情弹窗的数据。
 *
 * 时间一律按事件自身保存的 zoneId 换算，不用系统当前时区（与列表、统计同一口径）；
 * 各类型字段的中文标签复用 WaterFields / CoffeeFields / SportFields 的唯一来源，
 * 未填写的字段显式展示为「未记录」而不是隐藏，保证「完整信息」可读。
 * 未知类型只给基础信息并明确提示，不假装能解析它的详情。
 */
fun BackupRecord.toDetailView(): RecordDetailView {
    val zoneId = event.zoneId
    val timeLabel = formatAt(event.occurredAtEpochMillis, zoneId, shortTimeFormatter)

    val basic = DetailSection(
        title = "基本信息",
        fields = listOf(
            DetailField("发生时间", formatAt(event.occurredAtEpochMillis, zoneId, fullTimeFormatter)),
            DetailField("本地日期 / 时区", "${event.localDate} · $zoneId"),
            DetailField(
                label = "备注",
                value = event.note.ifBlank { "未填写" },
                muted = event.note.isBlank()
            )
        )
    )
    val details = DetailSection(title = "记录详情", fields = detailFields())
    val meta = DetailSection(
        title = "元数据",
        fields = listOf(
            DetailField("创建时间", formatAt(event.createdAtEpochMillis, zoneId, metaTimeFormatter)),
            DetailField("更新时间", formatAt(event.updatedAtEpochMillis, zoneId, metaTimeFormatter)),
            DetailField("记录 ID", event.id)
        )
    )
    return RecordDetailView(
        title = "${typeLabel(event.typeCode)} · $timeLabel",
        sections = listOf(basic, details, meta)
    )
}

private fun BackupRecord.detailFields(): List<DetailField> = when (event.typeCode) {
    DEFAULT_POOP_TYPE.code -> poopFields(detail as? RecordDetail.Poop)
    WaterFields.TYPE_CODE -> waterFields(genericFields())
    CoffeeFields.TYPE_CODE -> coffeeFields(genericFields())
    SportFields.TYPE_CODE -> sportFields(genericFields())
    IntimacyFields.TYPE_CODE -> intimacyFields(genericFields())
    MenstruationFields.TYPE_CODE -> menstruationFields(genericFields())
    else -> listOf(
        DetailField("详情", "该类型来自更新的版本，当前版本无法展示详情", muted = true)
    )
}

private fun BackupRecord.genericFields(): Map<String, String> =
    (detail as? RecordDetail.Generic)?.fields.orEmpty()

private fun poopFields(detail: RecordDetail.Poop?): List<DetailField> = listOf(
    field("布里斯托类型", detail?.bristolType?.let { "$it 型" }),
    field("排便量", detail?.amountCode?.let { amountLabels[it] }),
    field("费力程度", detail?.effortCode?.let { effortLabels[it] }),
    field("便意急迫感", detail?.urgency?.let { yesNo(it) }),
    field("腹部不适", detail?.abdominalDiscomfort?.let { yesNo(it) }),
    field("排不净感", detail?.incompleteEvacuation?.let { yesNo(it) })
)

private fun yesNo(value: Boolean): String = if (value) "是" else "否"

private fun waterFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("数量", fields[WaterFields.AMOUNT]),
    field("单位", fields[WaterFields.UNIT]?.let(WaterFields::unitLabel))
)

private fun coffeeFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("饮品", fields[CoffeeFields.DRINK]?.let(CoffeeFields::drinkLabel)),
    field("数量", fields[CoffeeFields.AMOUNT]),
    field("单位", fields[CoffeeFields.UNIT]?.let(CoffeeFields::unitLabel)),
    field("咖啡因", fields[CoffeeFields.CAFFEINE]?.let(CoffeeFields::caffeineLabel))
)

private fun sportFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("运动类型", fields[SportFields.SPORT]?.let(SportFields::sportLabel)),
    field("时长", fields[SportFields.DURATION]?.let(SportFields::durationLabel)),
    field("强度", fields[SportFields.INTENSITY]?.let(SportFields::intensityLabel))
)

private fun intimacyFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("保护措施", fields[IntimacyFields.PROTECTION]?.let(IntimacyFields::protectionLabel)),
    field("主观感受", fields[IntimacyFields.EXPERIENCE]?.let(IntimacyFields::experienceLabel)),
    field("时长", fields[IntimacyFields.DURATION]?.let(IntimacyFields::durationLabel))
)

private fun menstruationFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("流量", fields[MenstruationFields.FLOW]?.let(MenstruationFields::flowLabel)),
    field("痛经", fields[MenstruationFields.PAIN]?.let(MenstruationFields::painLabel)),
    field("症状", fields[MenstruationFields.SYMPTOMS]?.let(::symptomsLabel)),
    field("心情", fields[MenstruationFields.MOOD]?.let(MenstruationFields::moodLabel)),
    field("分泌物", fields[MenstruationFields.DISCHARGE]?.let(MenstruationFields::dischargeLabel))
)

/** 症状存 `|` 分隔的 code 串，展示时解码成中文并用顿号连接；日记走「备注」不在此列。 */
private fun symptomsLabel(value: String): String =
    MenstruationFields.decodeSymptoms(value)
        .joinToString("、") { MenstruationFields.symptomLabel(it) }
