package com.example.lifetrack.core.model

import java.time.LocalDate

/** 逐行表单的六行；[key] 复用字段常量，日记行只作 UI 标识，数据落在 `note`，不进 detail。 */
enum class PeriodField(val key: String) {
    FLOW(MenstruationFields.FLOW),
    PAIN(MenstruationFields.PAIN),
    SYMPTOMS(MenstruationFields.SYMPTOMS),
    MOOD(MenstruationFields.MOOD),
    DISCHARGE(MenstruationFields.DISCHARGE),
    DIARY("diary")
}

/**
 * 经期子应用逐行填写区的纯 JVM 状态机（设计 §5.4 / §5.8）。不依赖 Compose，可被
 * `testDebugUnitTest` 直接覆盖：选中日切换、展开互斥、症状 8 项上限、日记写 `note`、
 * 未来日期只读、与通用弹层编辑器同源（[toFields] 产出同一份 detail map）。
 *
 * 不可变：每个操作返回新实例。值全部来自 [MenstruationFields] 的枚举与白名单，
 * 不在这里另写一套校验。[readOnly] 为真（选中未来日）时所有写操作都是 no-op，
 * 保证「未来日期不能填写、不编造记录」。
 */
data class PeriodDayFormModel(
    val today: LocalDate,
    val selectedDate: LocalDate,
    val openRow: PeriodField? = null,
    val flow: String = "",
    val pain: String = "",
    val symptoms: List<String> = emptyList(),
    val mood: String = "",
    val discharge: String = "",
    val diary: String = ""
) {
    val readOnly: Boolean get() = selectedDate.isAfter(today)

    val hasContent: Boolean get() = PeriodField.entries.any { isFilled(it) }

    fun isFilled(field: PeriodField): Boolean = when (field) {
        PeriodField.FLOW -> flow.isNotEmpty()
        PeriodField.PAIN -> pain.isNotEmpty()
        PeriodField.SYMPTOMS -> symptoms.isNotEmpty()
        PeriodField.MOOD -> mood.isNotEmpty()
        PeriodField.DISCHARGE -> discharge.isNotEmpty()
        PeriodField.DIARY -> diary.isNotEmpty()
    }

    /** 切换选中日：载入那天的记录并收起展开行（与原型「点日期清空 openRow」一致）。 */
    fun selectDate(date: LocalDate, record: BackupRecord?): PeriodDayFormModel =
        of(today, date, record)

    /** 同一时刻只展开一行；再点同一行收起。未来日期只读，不展开。 */
    fun toggleRow(field: PeriodField): PeriodDayFormModel {
        if (readOnly) return this
        return copy(openRow = if (openRow == field) null else field)
    }

    fun closeRow(): PeriodDayFormModel = copy(openRow = null)

    /** 单选字段即点即存、再点取消。症状走 [toggleSymptom]，日记走 [setDiary]。 */
    fun selectOption(field: PeriodField, value: String): PeriodDayFormModel {
        if (readOnly) return this
        return when (field) {
            PeriodField.FLOW -> copy(flow = if (flow == value) "" else value)
            PeriodField.PAIN -> copy(pain = if (pain == value) "" else value)
            PeriodField.MOOD -> copy(mood = if (mood == value) "" else value)
            PeriodField.DISCHARGE -> copy(discharge = if (discharge == value) "" else value)
            PeriodField.SYMPTOMS, PeriodField.DIARY -> this
        }
    }

    /** 症状多选：已选则取消，未选且未达 8 项上限则加入；达上限不再添加（UI 另给提示）。 */
    fun toggleSymptom(code: String): PeriodDayFormModel {
        if (readOnly) return this
        val next = when {
            code in symptoms -> symptoms - code
            symptoms.size >= MenstruationFields.MAX_SYMPTOMS -> symptoms
            else -> symptoms + code
        }
        return copy(symptoms = next)
    }

    /** 日记即输入即写 `note`；长度上限沿用现有备注约束，不在此新增常量（W22）。 */
    fun setDiary(text: String): PeriodDayFormModel {
        if (readOnly) return this
        return copy(diary = text)
    }

    /** 产出 detail map：空值不写键，全空则返回空 map（上游据此落 `detail = null`）。 */
    fun toFields(): Map<String, String> = buildMap {
        if (flow.isNotEmpty()) put(MenstruationFields.FLOW, flow)
        if (pain.isNotEmpty()) put(MenstruationFields.PAIN, pain)
        if (symptoms.isNotEmpty()) {
            put(MenstruationFields.SYMPTOMS, MenstruationFields.encodeSymptoms(symptoms))
        }
        if (mood.isNotEmpty()) put(MenstruationFields.MOOD, mood)
        if (discharge.isNotEmpty()) put(MenstruationFields.DISCHARGE, discharge)
    }

    fun toNote(): String = diary

    companion object {
        /** 从某天的记录构建表单；[record] 为 null（那天还没记）时各字段为空。 */
        fun of(
            today: LocalDate,
            selectedDate: LocalDate,
            record: BackupRecord?
        ): PeriodDayFormModel {
            val fields = (record?.detail as? RecordDetail.Generic)?.fields.orEmpty()
            return PeriodDayFormModel(
                today = today,
                selectedDate = selectedDate,
                flow = fields[MenstruationFields.FLOW].orEmpty(),
                pain = fields[MenstruationFields.PAIN].orEmpty(),
                symptoms = MenstruationFields.decodeSymptoms(fields[MenstruationFields.SYMPTOMS].orEmpty()),
                mood = fields[MenstruationFields.MOOD].orEmpty(),
                discharge = fields[MenstruationFields.DISCHARGE].orEmpty(),
                diary = record?.event?.note.orEmpty()
            )
        }
    }
}