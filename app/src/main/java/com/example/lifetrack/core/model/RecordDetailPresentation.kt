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
    DEFAULT_POOP_TYPE.code -> poopFields(detail as? RecordDetail.Poop, event.zoneId)
    WaterFields.TYPE_CODE -> waterFields(genericFields())
    CoffeeFields.TYPE_CODE -> coffeeFields(genericFields())
    TeaFields.TYPE_CODE -> teaFields(genericFields())
    SportFields.TYPE_CODE -> sportFields(genericFields())
    IntimacyFields.TYPE_CODE -> intimacyFields(genericFields())
    MenstruationFields.TYPE_CODE -> menstruationFields(genericFields())
    else -> listOf(
        DetailField("详情", "该类型来自更新的版本，当前版本无法展示详情", muted = true)
    )
}

private fun BackupRecord.genericFields(): Map<String, String> =
    (detail as? RecordDetail.Generic)?.fields.orEmpty()

private fun poopFields(detail: RecordDetail.Poop?, zoneId: String): List<DetailField> = listOf(
    field("布里斯托类型", detail?.bristolType?.let { "$it 型" }),
    field("排便量", detail?.amountCode?.let { amountLabels[it] }),
    field("费力程度", detail?.effortCode?.let { effortLabels[it] }),
    field("便意急迫感", detail?.urgency?.let { yesNo(it) }),
    field("腹部不适", detail?.abdominalDiscomfort?.let { yesNo(it) }),
    field("排不净感", detail?.incompleteEvacuation?.let { yesNo(it) }),
    // 用时与结束时间是同一件事的两个视角：手动填用时只有前者，自动结束两者都有。
    field("用时", detail?.durationMinutes?.let(PoopFields::durationLabel)),
    field(
        "结束时间",
        detail?.endedAtEpochMillis?.let { formatAt(it, zoneId, shortTimeFormatter) }
    )
)

private fun yesNo(value: Boolean): String = if (value) "是" else "否"

private fun waterFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("数量", fields[WaterFields.AMOUNT]),
    field("单位", fields[WaterFields.UNIT]?.let(WaterFields::unitLabel))
)

/**
 * 咖啡详情：新字段优先；旧记录没有 variety 时退回展示旧「饮品 / 咖啡因」，
 * 不让历史数据在详情弹层里变成一片「未记录」。
 */
private fun coffeeFields(fields: Map<String, String>): List<DetailField> {
    val isLegacy = fields[CoffeeFields.VARIETY] == null
    return buildList {
        if (isLegacy) {
            add(field("饮品", fields[CoffeeFields.DRINK]?.let(CoffeeFields::drinkLabel)))
        } else {
            add(field("品种", fields[CoffeeFields.VARIETY]))
            add(field("杯型", fields[CoffeeFields.SIZE]?.let(CoffeeFields::sizeLabel)))
            add(field("温度", fields[CoffeeFields.TEMPERATURE]?.let(CoffeeFields::temperatureLabel)))
            add(field("咖啡豆", fields[CoffeeFields.BEANS]?.let(CoffeeFields::beansLabel)))
            add(field("浓度", fields[CoffeeFields.CONCENTRATION]?.let(CoffeeFields::concentrationLabel)))
            add(field("糖度", fields[CoffeeFields.SUGAR]?.let(CoffeeFields::sugarLabel)))
            add(field("奶油", fields[CoffeeFields.CREAM]?.let(CoffeeFields::creamLabel)))
        }
        add(field("份量", fields[CoffeeFields.AMOUNT]?.let { "${it}ml" }))
        add(field("热量", fields[CoffeeFields.CALORIES]?.let { "$it 大卡" }))
        if (isLegacy) {
            add(field("单位", fields[CoffeeFields.UNIT]?.let(CoffeeFields::unitLabel)))
            add(field("咖啡因", fields[CoffeeFields.CAFFEINE]?.let(CoffeeFields::caffeineLabel)))
        }
    }
}

private fun teaFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("茶种", fields[TeaFields.VARIETY]),
    field("杯数", fields[TeaFields.CUPS]?.let { "$it 杯" }),
    field("毫升", fields[TeaFields.AMOUNT]?.let { "${it}ml" }),
    field("热量", fields[TeaFields.CALORIES]?.let { "$it 大卡" })
)

private fun sportFields(fields: Map<String, String>): List<DetailField> = listOf(
    field("运动类型", fields[SportFields.SPORT]?.let(SportFields::sportLabel)),
    field("时长", fields[SportFields.DURATION]?.let(SportFields::durationLabel)),
    field("强度", fields[SportFields.INTENSITY]?.let(SportFields::intensityLabel))
)

/**
 * 亲密关系：重构后主字段是「措施」。旧记录没有 `method` 键时退回显示旧版「保护措施」，
 * 不把已经记过的老数据说成「未记录」；感受与时长只在存在时附加，不再占固定行。
 */
private fun intimacyFields(fields: Map<String, String>): List<DetailField> {
    val method = fields[IntimacyFields.METHOD]
    val primary = if (method != null) {
        field("措施", IntimacyFields.methodLabel(method))
    } else {
        field("措施", fields[IntimacyFields.PROTECTION]?.let(IntimacyFields::protectionLabel))
    }
    return listOfNotNull(
        primary,
        fields[IntimacyFields.EXPERIENCE]?.let { field("主观感受", IntimacyFields.experienceLabel(it)) },
        fields[IntimacyFields.DURATION]?.let { field("时长", IntimacyFields.durationLabel(it)) }
    )
}

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
