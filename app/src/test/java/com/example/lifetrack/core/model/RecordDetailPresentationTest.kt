package com.example.lifetrack.core.model

import com.example.lifetrack.core.database.entity.TrackEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecordDetailPresentationTest {

    private val utcInstant = Instant.parse("2026-11-10T00:30:00Z").toEpochMilli()

    private fun event(
        typeCode: String = "poop",
        occurredAt: Long = utcInstant,
        zoneId: String = "Asia/Shanghai",
        note: String = "早餐后",
        id: String = "3f9c2a10-1111-2222-3333-444455556666"
    ) = TrackEventEntity(
        id = id,
        typeCode = typeCode,
        occurredAtEpochMillis = occurredAt,
        localDate = "2026-11-10",
        zoneId = zoneId,
        note = note,
        createdAtEpochMillis = occurredAt + 60_000,
        updatedAtEpochMillis = occurredAt + 120_000
    )

    private fun generic(typeCode: String, fields: Map<String, String>) =
        BackupRecord(event(typeCode), RecordDetail.Generic(typeCode, fields))

    private fun values(section: DetailSection): List<String> = section.fields.map { it.value }

    @Test
    fun `poop detail lists all six fields`() {
        val view = BackupRecord(
            event(),
            RecordDetail.Poop(
                bristolType = 4,
                amountCode = 2,
                effortCode = 1,
                urgency = false,
                abdominalDiscomfort = true,
                incompleteEvacuation = false
            )
        ).toDetailView()

        assertEquals("排便 · 08:30", view.title)
        assertEquals(listOf("基本信息", "记录详情", "元数据"), view.sections.map { it.title })
        assertEquals(
            listOf("4 型", "适中", "不费力", "否", "是", "否"),
            values(view.sections[1])
        )
        assertTrue(view.sections[1].fields.none { it.muted })
    }

    @Test
    fun `quick record without detail shows explicit placeholders`() {
        val view = BackupRecord(event()).toDetailView()
        val details = view.sections[1]

        assertEquals(6, details.fields.size)
        assertTrue(details.fields.all { it.value == "未记录" && it.muted })
    }

    @Test
    fun `water detail shows amount and unit`() {
        val view = generic("water", mapOf("amountMl" to "300", "unit" to "cup")).toDetailView()

        assertEquals("饮水 · 08:30", view.title)
        assertEquals(listOf("300", "杯"), values(view.sections[1]))
    }

    @Test
    fun `coffee detail shows drink amount unit and caffeine`() {
        val view = generic(
            "coffee",
            mapOf(
                "drinkType" to "tea",
                "amountMl" to "250",
                "unit" to "ml",
                "caffeine" to "low"
            )
        ).toDetailView()

        assertEquals(listOf("茶", "250", "ml", "低因"), values(view.sections[1]))
    }

    @Test
    fun `sport detail converts duration and labels intensity`() {
        val view = generic(
            "sport",
            mapOf("sportType" to "ride", "durationMin" to "80", "intensity" to "medium")
        ).toDetailView()

        assertEquals(listOf("骑行", "1 小时 20 分", "中等强度"), values(view.sections[1]))
    }

    @Test
    fun `intimacy detail labels all three fields`() {
        val view = generic(
            IntimacyFields.TYPE_CODE,
            mapOf("protection" to "na", "experience" to "okay", "durationMin" to "80")
        ).toDetailView()

        assertEquals("亲密关系 · 08:30", view.title)
        assertEquals(listOf("不适用", "一般", "1 小时 20 分"), values(view.sections[1]))
    }

    @Test
    fun `intimacy detail without any field shows placeholders`() {
        val view = generic(IntimacyFields.TYPE_CODE, emptyMap()).toDetailView()

        assertEquals(3, view.sections[1].fields.size)
        assertTrue(view.sections[1].fields.all { it.value == "未记录" && it.muted })
    }

    @Test
    fun `menstruation detail labels all five fields`() {
        val view = generic(
            MenstruationFields.TYPE_CODE,
            mapOf(
                MenstruationFields.FLOW to "medium",
                MenstruationFields.PAIN to "mild",
                MenstruationFields.SYMPTOMS to "cramps|backache|headache",
                MenstruationFields.MOOD to "low",
                MenstruationFields.DISCHARGE to "eggwhite"
            )
        ).toDetailView()

        assertEquals("月经 · 08:30", view.title)
        assertEquals(
            listOf("中", "轻", "腹痛、腰酸、头痛", "低落", "蛋清"),
            values(view.sections[1])
        )
        assertTrue(view.sections[1].fields.none { it.muted })
    }

    @Test
    fun `menstruation detail without any field shows placeholders`() {
        val view = generic(MenstruationFields.TYPE_CODE, emptyMap()).toDetailView()

        assertEquals(5, view.sections[1].fields.size)
        assertTrue(view.sections[1].fields.all { it.value == "未记录" && it.muted })
    }

    @Test
    fun `menstruation symptoms field decodes pipe separated codes`() {
        val view = generic(
            MenstruationFields.TYPE_CODE,
            mapOf(MenstruationFields.SYMPTOMS to "fatigue|insomnia")
        ).toDetailView()

        assertEquals("乏力、失眠", view.sections[1].fields[2].value)
    }

    @Test
    fun `menstruation diary lives in note not in detail fields`() {
        val view = BackupRecord(
            event(typeCode = MenstruationFields.TYPE_CODE, note = "今天有点累"),
            RecordDetail.Generic(
                MenstruationFields.TYPE_CODE,
                mapOf(MenstruationFields.FLOW to "light")
            )
        ).toDetailView()

        assertEquals("今天有点累", view.sections[0].fields[2].value)
        assertEquals(5, view.sections[1].fields.size)
        assertEquals("少", view.sections[1].fields[0].value)
    }

    @Test
    fun `missing generic fields are marked as not recorded`() {
        val view = generic("water", mapOf("amountMl" to "200")).toDetailView()

        assertEquals(listOf("200", "未记录"), values(view.sections[1]))
        assertFalse(view.sections[1].fields[0].muted)
        assertTrue(view.sections[1].fields[1].muted)
    }

    @Test
    fun `unknown type explains that details cannot be shown`() {
        val view = BackupRecord(event(typeCode = "mood")).toDetailView()
        val details = view.sections[1]

        assertEquals("mood · 08:30", view.title)
        assertEquals(1, details.fields.size)
        assertTrue(details.fields[0].muted)
        assertTrue(details.fields[0].value.contains("当前版本无法展示"))
    }

    @Test
    fun `time follows the zone saved on the event`() {
        val tokyo = BackupRecord(event(zoneId = "Asia/Tokyo")).toDetailView()
        val losAngeles = BackupRecord(event(zoneId = "America/Los_Angeles")).toDetailView()

        val tokyoTime = tokyo.sections[0].fields[0].value
        val laTime = losAngeles.sections[0].fields[0].value
        assertTrue(tokyoTime.contains("09:30"))
        assertNotEquals(tokyoTime, laTime)
        assertEquals("2026-11-10 · Asia/Tokyo", tokyo.sections[0].fields[1].value)
    }

    @Test
    fun `invalid zone falls back to unknown time instead of crashing`() {
        val view = BackupRecord(event(zoneId = "Not/A_Zone")).toDetailView()

        assertEquals("排便 · 时间未知", view.title)
        assertEquals("时间未知", view.sections[0].fields[0].value)
        assertEquals("时间未知", view.sections[2].fields[0].value)
    }

    @Test
    fun `blank note is shown as placeholder`() {
        val view = BackupRecord(event(note = "  ")).toDetailView()
        val note = view.sections[0].fields[2]

        assertEquals("未填写", note.value)
        assertTrue(note.muted)
    }

    @Test
    fun `meta section exposes timestamps and record id`() {
        val view = BackupRecord(event()).toDetailView()
        val meta = view.sections[2]

        assertEquals(listOf("创建时间", "更新时间", "记录 ID"), meta.fields.map { it.label })
        assertEquals("2026-11-10 08:31", meta.fields[0].value)
        assertEquals("2026-11-10 08:32", meta.fields[1].value)
        assertEquals("3f9c2a10-1111-2222-3333-444455556666", meta.fields[2].value)
    }
}
