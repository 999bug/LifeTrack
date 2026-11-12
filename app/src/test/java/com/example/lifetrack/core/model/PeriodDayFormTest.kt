package com.example.lifetrack.core.model

import com.example.lifetrack.core.backup.DetailSpecRegistry
import com.example.lifetrack.core.database.entity.TrackEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** A9：逐行表单状态机（设计 §5.8）——选中日切换 / 展开互斥 / 症状上限 / 日记写 note / 未来只读 / 同源。 */
class PeriodDayFormTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun record(
        date: String,
        fields: Map<String, String> = emptyMap(),
        note: String = ""
    ): BackupRecord {
        val millis = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        return BackupRecord(
            event = TrackEventEntity(
                id = "m-$date",
                typeCode = MenstruationFields.TYPE_CODE,
                occurredAtEpochMillis = millis,
                localDate = date,
                zoneId = zone.id,
                note = note,
                createdAtEpochMillis = millis,
                updatedAtEpochMillis = millis
            ),
            detail = if (fields.isEmpty()) null
            else RecordDetail.Generic(MenstruationFields.TYPE_CODE, fields)
        )
    }

    private fun model(
        date: LocalDate = today,
        record: BackupRecord? = null
    ) = PeriodDayFormModel.of(today, date, record)

    @Test
    fun `loads fields and diary from an existing record`() {
        val record = record(
            "2026-11-12",
            mapOf(
                MenstruationFields.FLOW to "medium",
                MenstruationFields.PAIN to "mild",
                MenstruationFields.SYMPTOMS to "cramps|backache",
                MenstruationFields.MOOD to "calm",
                MenstruationFields.DISCHARGE to "sticky"
            ),
            note = "第一天，量不多。"
        )
        val form = model(record = record)

        assertEquals("medium", form.flow)
        assertEquals("mild", form.pain)
        assertEquals(listOf("cramps", "backache"), form.symptoms)
        assertEquals("calm", form.mood)
        assertEquals("sticky", form.discharge)
        assertEquals("第一天，量不多。", form.diary)
        assertEquals("第一天，量不多。", form.toNote())
        assertFalse(form.readOnly)
    }

    @Test
    fun `a day without record starts empty`() {
        val form = model(record = null)

        assertTrue(form.toFields().isEmpty())
        assertEquals("", form.toNote())
        assertFalse(form.hasContent)
        assertNull(form.openRow)
    }

    @Test
    fun `switching selected date loads that day and collapses the open row`() {
        val first = model(
            date = LocalDate.of(2026, 11, 10),
            record = record("2026-11-10", mapOf(MenstruationFields.FLOW to "light"))
        ).toggleRow(PeriodField.FLOW)
        assertEquals(PeriodField.FLOW, first.openRow)

        val second = first.selectDate(
            LocalDate.of(2026, 11, 11),
            record("2026-11-11", mapOf(MenstruationFields.FLOW to "heavy"), note = "第二天")
        )

        assertEquals(LocalDate.of(2026, 11, 11), second.selectedDate)
        assertEquals("heavy", second.flow)
        assertEquals("第二天", second.diary)
        assertNull(second.openRow)
    }

    @Test
    fun `only one row stays open at a time`() {
        val form = model()
            .toggleRow(PeriodField.FLOW)
            .toggleRow(PeriodField.PAIN)
        assertEquals(PeriodField.PAIN, form.openRow)

        assertEquals(PeriodField.FLOW, form.toggleRow(PeriodField.FLOW).openRow)
        assertNull(form.toggleRow(PeriodField.PAIN).openRow)
        assertNull(form.closeRow().openRow)
    }

    @Test
    fun `single select toggles on then off`() {
        val on = model().selectOption(PeriodField.FLOW, "medium")
        assertEquals("medium", on.flow)
        assertEquals("", on.selectOption(PeriodField.FLOW, "medium").flow)
        assertEquals("heavy", on.selectOption(PeriodField.FLOW, "heavy").flow)
    }

    @Test
    fun `symptoms add remove and cap at eight`() {
        var form = model()
        MenstruationFields.SYMPTOM_CODES.take(8).forEach { form = form.toggleSymptom(it) }
        assertEquals(8, form.symptoms.size)

        val ninth = MenstruationFields.SYMPTOM_CODES[8]
        form = form.toggleSymptom(ninth)
        assertEquals(8, form.symptoms.size)
        assertFalse(ninth in form.symptoms)

        form = form.toggleSymptom(MenstruationFields.SYMPTOM_CODES.first())
        assertEquals(7, form.symptoms.size)
        form = form.toggleSymptom(ninth)
        assertEquals(8, form.symptoms.size)
        assertTrue(ninth in form.symptoms)
    }

    @Test
    fun `diary writes straight to note`() {
        val form = model().setDiary("今天腹痛明显，晚上早睡。")

        assertEquals("今天腹痛明显，晚上早睡。", form.toNote())
        assertTrue(form.isFilled(PeriodField.DIARY))
        assertTrue(form.toFields().isEmpty())
    }

    @Test
    fun `future date is read only and every mutation is a no-op`() {
        val future = LocalDate.of(2026, 11, 20)
        val form = model(date = future)

        assertTrue(form.readOnly)
        assertEquals(form, form.toggleRow(PeriodField.FLOW))
        assertEquals(form, form.selectOption(PeriodField.FLOW, "heavy"))
        assertEquals(form, form.toggleSymptom("cramps"))
        assertEquals(form, form.setDiary("不该写进去"))
        assertNull(form.openRow)
        assertTrue(form.toFields().isEmpty())
    }

    @Test
    fun `today and past dates are editable`() {
        assertFalse(model(date = today).readOnly)
        assertFalse(model(date = LocalDate.of(2026, 11, 1)).readOnly)
    }

    @Test
    fun `to fields omits empty keys and encodes symptoms`() {
        val form = model()
            .selectOption(PeriodField.FLOW, "medium")
            .selectOption(PeriodField.MOOD, "calm")
            .toggleSymptom("cramps")
            .toggleSymptom("fatigue")
            .setDiary("只写日记不进 detail")

        assertEquals(
            mapOf(
                MenstruationFields.FLOW to "medium",
                MenstruationFields.SYMPTOMS to "cramps|fatigue",
                MenstruationFields.MOOD to "calm"
            ),
            form.toFields()
        )
        assertEquals("只写日记不进 detail", form.toNote())
    }

    @Test
    fun `all empty produces an empty detail map so upstream stores null`() {
        assertTrue(model().toFields().isEmpty())
        assertFalse(model().hasContent)
    }

    @Test
    fun `form and generic editor share the same field encoding`() {
        val form = model()
            .selectOption(PeriodField.PAIN, "severe")
            .selectOption(PeriodField.DISCHARGE, "eggwhite")
            .toggleSymptom("headache")

        val editorFields = mapOf(
            MenstruationFields.PAIN to "severe",
            MenstruationFields.SYMPTOMS to MenstruationFields.encodeSymptoms(listOf("headache")),
            MenstruationFields.DISCHARGE to "eggwhite"
        )
        assertEquals(editorFields, form.toFields())
        assertTrue(
            DetailSpecRegistry.specFor(MenstruationFields.TYPE_CODE)!!.isFieldsValid(form.toFields())
        )
    }

    @Test
    fun `has content reflects any filled row`() {
        assertFalse(model().hasContent)
        assertTrue(model().selectOption(PeriodField.FLOW, "light").hasContent)
        assertTrue(model().setDiary("一句话").hasContent)
        assertTrue(model().toggleSymptom("cramps").hasContent)
    }
}