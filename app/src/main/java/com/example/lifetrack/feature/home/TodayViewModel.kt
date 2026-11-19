package com.example.lifetrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.ui.ActionFeedback
import com.example.lifetrack.core.ui.launchAction
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.TrackRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val QUICK_WATER_AMOUNT_ML = 250

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: TrackRepository,
    private val todaySource: TodaySource
) : ViewModel() {
    val actionFeedback = ActionFeedback()
    val actionMessages = actionFeedback.messages

    val today: StateFlow<LocalDate> = todaySource.today

    val trackTypes: StateFlow<List<TrackTypeEntity>> = repository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun refreshToday() {
        todaySource.refresh()
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(todaySource.millisUntilNextDay())
                refreshToday()
            }
        }
        viewModelScope.launch {
            repository.ensureDefaultTypes()
            repository.migrateLegacyTeaRecords()
        }
    }
    val todayRecords: StateFlow<List<BackupRecord>> = today
        .flatMapLatest { repository.observeRecordsForDate(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun quickRecord() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            repository.addPoopRecord(
                PoopRecordDraft(
                    occurredAtEpochMillis = Instant.now().toEpochMilli(),
                    zoneId = zone.id
                )
            )
            refreshToday()
        }
    }

    /**
     * 首页饮水卡的一键记录：默认一杯 250 ml。
     * 与排便一样属于「有合理默认值」的动作，因此不在首页弹编辑器；
     * 需要改份量时走卡片上的「详细」。
     */
    fun quickWaterRecord() {
        viewModelScope.launch {
            repository.addRecord(
                RecordDraft(
                    typeCode = WaterFields.TYPE_CODE,
                    occurredAtEpochMillis = Instant.now().toEpochMilli(),
                    zoneId = ZoneId.systemDefault().id,
                    detail = RecordDetail.Generic(
                        typeCode = WaterFields.TYPE_CODE,
                        fields = mapOf(
                            WaterFields.AMOUNT to QUICK_WATER_AMOUNT_ML.toString(),
                            WaterFields.UNIT to "ml"
                        )
                    )
                )
            )
            refreshToday()
        }
    }

    private val poopFeedbackChannel = Channel<String>(Channel.BUFFERED)

    /** 列表里「结束」的一次性反馈（用时已记录 / 超过阈值未记录），首页收集后弹 Snackbar。 */
    val poopFeedback = poopFeedbackChannel.receiveAsFlow()

    /**
     * 在今日列表里直接结束一条进行中的排便记录：把「现在」写成结束时间并算出用时。
     *
     * 用时口径全部交给 [PoopFields.resolve]：超过 60 分钟不写用时（记录本身保留），
     * 只回一条提示让用户改用手填。已有用时的记录不再重复结束，避免把历史值覆盖成 0。
     */
    fun endPoopRecord(record: BackupRecord) {
        val detail = record.detail as? RecordDetail.Poop
        if (detail?.durationMinutes != null) return
        viewModelScope.launch {
            val timing = PoopFields.resolve(
                startEpochMillis = record.event.occurredAtEpochMillis,
                endedAtEpochMillis = System.currentTimeMillis(),
                manualMinutes = ""
            )
            repository.updatePoopRecord(
                eventId = record.event.id,
                draft = PoopRecordDraft(
                    occurredAtEpochMillis = record.event.occurredAtEpochMillis,
                    zoneId = record.event.zoneId,
                    bristolType = detail?.bristolType,
                    amountCode = detail?.amountCode,
                    effortCode = detail?.effortCode,
                    urgency = detail?.urgency,
                    abdominalDiscomfort = detail?.abdominalDiscomfort,
                    incompleteEvacuation = detail?.incompleteEvacuation,
                    note = record.event.note,
                    endedAtEpochMillis = timing.endedAtEpochMillis,
                    durationMinutes = timing.durationMinutes
                )
            )
            refreshToday()
            poopFeedbackChannel.trySend(
                timing.durationMinutes?.let { "本次用时 $it 分钟" }
                    ?: "已超过 ${PoopFields.MAX_DURATION_MINUTES} 分钟，用时未记录；可在编辑里手动填写"
            )
        }
    }

    fun saveRecord(eventId: String?, draft: PoopRecordDraft) {
        viewModelScope.launch {
            if (eventId == null) repository.addPoopRecord(draft)
            else repository.updatePoopRecord(eventId, draft)
            refreshToday()
        }
    }

    /** 新类型（饮水等）走通用详情表；排便仍走上面的强类型路径。 */
    fun saveGenericRecord(eventId: String?, draft: RecordDraft) {
        viewModelScope.launch {
            if (eventId == null) repository.addRecord(draft)
            else repository.updateRecord(eventId, draft)
            refreshToday()
        }
    }

    /**
     * 子应用逐行填写：把选中日的体征 + 日记 upsert 成一条月经事件，按 `(typeCode, localDate)` 幂等。
     * [fields] 已由 [com.example.lifetrack.core.model.PeriodDayFormModel.toFields] 收敛（空键不写、症状编码），
     * 全空时落 `detail = null` 的仅标记事件，与恢复路径一致。
     */
    fun deleteRecord(eventId: String) {
        viewModelScope.launch {
            repository.deletePoopRecord(eventId)
            refreshToday()
        }
    }


    class Factory(
        private val repository: TrackRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(TodayViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return TodayViewModel(repository, todaySource) as T
        }
    }
}