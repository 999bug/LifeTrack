package com.example.lifetrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.ui.ActionFeedback
import com.example.lifetrack.core.ui.launchAction
import com.example.lifetrack.core.backup.BackupCodec
import com.example.lifetrack.core.backup.BackupDecodeResult
import com.example.lifetrack.core.backup.BackupPayload
import com.example.lifetrack.core.backup.BackupReencryptResult
import com.example.lifetrack.domain.model.TrackType
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.model.CalendarMonth
import com.example.lifetrack.core.model.IntimacyFields
import com.example.lifetrack.core.model.HistoryFilter
import com.example.lifetrack.core.model.HistoryQuery
import com.example.lifetrack.core.model.ModuleOrdering
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.StatsPeriod
import com.example.lifetrack.core.model.ThemeMode
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.model.CycleCalculator
import com.example.lifetrack.core.model.CycleSnapshot
import com.example.lifetrack.core.model.DateRange
import com.example.lifetrack.core.model.MenstruationFields
import com.example.lifetrack.core.model.PoopFields
import com.example.lifetrack.core.model.PeriodEndEdit
import com.example.lifetrack.core.model.PeriodFill
import com.example.lifetrack.core.model.PeriodOngoing
import com.example.lifetrack.core.model.PeriodStartEdit
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.repository.BackupRepository
import com.example.lifetrack.domain.repository.DatabaseMaintenanceRepository
import com.example.lifetrack.domain.repository.TypeCatalogRepository
import com.example.lifetrack.feature.history.DayCalendarData
import com.example.lifetrack.feature.statistics.PeriodStats
import com.example.lifetrack.feature.statistics.emptyStatistics
import com.example.lifetrack.feature.statistics.observeStatistics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 首页饮水卡一键记录的默认杯量（ml）。 */
private const val QUICK_WATER_AMOUNT_ML = 250

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val typeCatalogRepository: TypeCatalogRepository,
    private val backupRepository: BackupRepository,
    private val maintenanceRepository: DatabaseMaintenanceRepository,
    private val settingsRepository: SettingsRepository,
    private val todaySource: TodaySource
) : ViewModel() {
    val actionFeedback = ActionFeedback()
    val actionMessages = actionFeedback.messages


    /** 应用内唯一的业务日期；页面不得再自行调用 LocalDate.now()。 */
    val today: StateFlow<LocalDate> = todaySource.today

    /** 记录项目（启用状态 + 排序）。首次进入时补齐内置类型，之后由 Room Flow 驱动。 */
    val trackTypes: StateFlow<List<TrackType>> = typeCatalogRepository.observeTrackTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)

    /** 用户自定义的咖啡 / 茶品种（预设品种另在 CoffeeFields/TeaFields 里固定）。 */
    val customCoffeeVarieties: StateFlow<List<String>> = settingsRepository.customCoffeeVarieties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customTeaVarieties: StateFlow<List<String>> = settingsRepository.customTeaVarieties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCustomCoffeeVariety(name: String) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { settingsRepository.addCustomCoffeeVariety(name) }
    }

    fun addCustomTeaVariety(name: String) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { settingsRepository.addCustomTeaVariety(name) }
    }

    fun removeCustomCoffeeVariety(name: String) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { settingsRepository.removeCustomCoffeeVariety(name) }
    }

    fun removeCustomTeaVariety(name: String) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { settingsRepository.removeCustomTeaVariety(name) }
    }


    fun setTypeEnabled(code: String, enabled: Boolean) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { typeCatalogRepository.setTypeEnabled(code, enabled) }
    }

    fun moveType(code: String, offset: Int) {
        val current = trackTypes.value
        val reordered = ModuleOrdering.move(current, code, offset)
        if (reordered != current) {
            viewModelScope.launchAction(actionFeedback, "操作失败") { typeCatalogRepository.reorderTypes(reordered) }
        }
    }

    suspend fun buildBackupJson(passphrase: CharArray? = null): String = withContext(Dispatchers.IO) {
        val payload = backupRepository.readBackup()
        if (passphrase == null) {
            BackupCodec.encode(payload.types, payload.events)
        } else {
            BackupCodec.encodeEncrypted(payload.types, payload.events, passphrase)
        }
    }

    /** 未给口令时，加密备份会返回 `PassphraseRequired`，由设置页弹口令输入。 */
    suspend fun decodeBackupJson(
        text: String,
        passphrase: CharArray? = null
    ): BackupDecodeResult = withContext(Dispatchers.IO) {
        BackupCodec.decode(text, passphrase = passphrase)
    }

    /** 口令轮换：旧口令解出、新口令重加密，返回完整的新文件内容。 */
    suspend fun reencryptBackup(
        text: String,
        oldPassphrase: CharArray,
        newPassphrase: CharArray
    ): BackupReencryptResult = withContext(Dispatchers.IO) {
        BackupCodec.reencrypt(text, oldPassphrase, newPassphrase)
    }

    suspend fun restoreBackup(payload: BackupPayload) {
        withContext(Dispatchers.IO) { backupRepository.restoreBackup(payload) }
        // 恢复是整体替换：旧的「进行中起点」可能指向已不存在的数据，直接清掉，
        // 让用户在新数据上自己决定要不要重新点「大姨妈来了」。
        settingsRepository.setOngoingPeriodStart(null)
        todaySource.refresh()
    }

    suspend fun countAllRecords(): Int = withContext(Dispatchers.IO) {
        backupRepository.countAllRecords()
    }

    /** 「整理数据库」的状态。只在设置页数据区使用，是一次性维护动作。 */
    sealed interface CompactState {
        data object Idle : CompactState
        data object Running : CompactState

        /** 整理前后的文件大小，用来告诉用户实际回收了多少。 */
        data class Done(val beforeBytes: Long, val afterBytes: Long) : CompactState
        data class Failed(val message: String) : CompactState
    }

    private val _compactState = MutableStateFlow<CompactState>(CompactState.Idle)
    val compactState: StateFlow<CompactState> = _compactState.asStateFlow()

    /** 当前数据库文件大小；读不到时返回 0，UI 显示「—」。 */
    suspend fun databaseSizeBytes(): Long =
        runCatching { maintenanceRepository.databaseSizeBytes() }.getOrDefault(0L)

    fun compactDatabase() {
        if (_compactState.value is CompactState.Running) return
        viewModelScope.launchAction(actionFeedback, "操作失败") {
            _compactState.value = CompactState.Running
            val before = runCatching { maintenanceRepository.databaseSizeBytes() }.getOrDefault(0L)
            runCatching { maintenanceRepository.compactDatabase() }
                .onSuccess { after -> _compactState.value = CompactState.Done(before, after) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _compactState.value = CompactState.Failed(e.message ?: "整理失败")
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launchAction(actionFeedback, "操作失败") { settingsRepository.setThemeMode(mode) }
    }

    class Factory(
        private val typeCatalogRepository: TypeCatalogRepository,
    private val backupRepository: BackupRepository,
    private val maintenanceRepository: DatabaseMaintenanceRepository,
        private val settingsRepository: SettingsRepository,
        private val todaySource: TodaySource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return SettingsViewModel(typeCatalogRepository, backupRepository, maintenanceRepository, settingsRepository, todaySource) as T
        }
    }
}
