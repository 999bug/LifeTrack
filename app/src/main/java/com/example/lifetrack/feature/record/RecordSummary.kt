package com.example.lifetrack.feature.record

import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.CoffeeFields
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.SportFields
import com.example.lifetrack.core.model.WaterFields
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 一条记录的「一行式」摘要，供日期详情弹层这类紧凑列表使用。
 *
 * 文案与各类型的 RecordItem 保持一致（同样取 量 / 时长 / 强度 这些主字段），
 * 但不含编辑、删除按钮，也不展开次要字段——这里回答的是「那天记了什么」，
 * 而不是「这条记录的全部内容」；后者仍由 RecordDetailSheet 负责。
 */
object RecordSummary {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val amountLabels = mapOf(1 to "少量", 2 to "适中", 3 to "大量")
    private val effortLabels = mapOf(1 to "不费力", 2 to "轻微费力", 3 to "明显费力")

    fun time(record: BackupRecord): String = runCatching {
        Instant.ofEpochMilli(record.event.occurredAtEpochMillis)
            .atZone(ZoneId.of(record.event.zoneId))
            .format(timeFormatter)
    }.getOrDefault("时间未知")

    /** 优先用用户自定义的模块名，缺省时回落到内置名，最后才显示类型码。 */
    fun typeName(typeCode: String, names: Map<String, String>): String =
        names[typeCode] ?: DEFAULT_NAMES[typeCode] ?: typeCode

    fun detail(record: BackupRecord): String {
        val note = record.event.note
        return when (record.event.typeCode) {
            DEFAULT_POOP_TYPE.code -> {
                val detail = record.detail as? RecordDetail.Poop
                buildList {
                    detail?.bristolType?.let { add("布里斯托 $it 型") }
                    detail?.amountCode?.let { code -> amountLabels[code]?.let { add(it) } }
                    detail?.effortCode?.let { code -> effortLabels[code]?.let { add(it) } }
                    if (note.isNotBlank()) add(note)
                }.joinToString(" · ").ifBlank { "尚未填写详情" }
            }

            WaterFields.TYPE_CODE -> {
                val fields = genericFields(record)
                val amount = fields[WaterFields.AMOUNT]
                val unit = WaterFields.unitLabel(fields[WaterFields.UNIT].orEmpty())
                buildList {
                    if (amount != null) add("$amount $unit")
                    if (note.isNotBlank()) add(note)
                }.joinToString(" · ").ifBlank { "尚未填写详情" }
            }

            CoffeeFields.TYPE_CODE -> {
                val fields = genericFields(record)
                val amount = fields[CoffeeFields.AMOUNT]
                val unit = CoffeeFields.unitLabel(fields[CoffeeFields.UNIT].orEmpty())
                buildList {
                    fields[CoffeeFields.DRINK]?.let { add(CoffeeFields.drinkLabel(it)) }
                    if (amount != null) add("$amount $unit")
                    fields[CoffeeFields.CAFFEINE]?.let { add(CoffeeFields.caffeineLabel(it)) }
                    if (note.isNotBlank()) add(note)
                }.joinToString(" · ").ifBlank { "尚未填写详情" }
            }

            SportFields.TYPE_CODE -> {
                val fields = genericFields(record)
                buildList {
                    fields[SportFields.SPORT]?.let { add(SportFields.sportLabel(it)) }
                    fields[SportFields.DURATION]?.let { add(SportFields.durationLabel(it)) }
                    fields[SportFields.INTENSITY]?.let { add(SportFields.intensityLabel(it)) }
                    if (note.isNotBlank()) add(note)
                }.joinToString(" · ").ifBlank { "尚未填写详情" }
            }

            // 高敏感类型：弹层里只说明「记了一条」，不展示任何字段值，与 L1 收敛一致。
            IntimacyFields.TYPE_CODE -> "已记录"

            else -> "该类型来自更新的版本，当前版本无法展示详情"
        }
    }

    private fun genericFields(record: BackupRecord): Map<String, String> =
        (record.detail as? RecordDetail.Generic)?.fields.orEmpty()

    private val DEFAULT_NAMES = mapOf(
        "poop" to "排便",
        WaterFields.TYPE_CODE to "饮水",
        CoffeeFields.TYPE_CODE to "咖啡 / 茶",
        SportFields.TYPE_CODE to "运动",
        IntimacyFields.TYPE_CODE to "亲密关系"
    )
}
