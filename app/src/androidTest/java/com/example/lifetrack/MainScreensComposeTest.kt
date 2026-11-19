package com.example.lifetrack

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.model.BackupRecord
import com.example.lifetrack.core.model.RecordDetail
import com.example.lifetrack.core.model.WaterFields
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.core.ui.LifeTrackTheme
import com.example.lifetrack.domain.model.DailyCount
import com.example.lifetrack.domain.model.DailyTypeCount
import com.example.lifetrack.domain.model.EventTimestamp
import com.example.lifetrack.domain.model.HistoryPage
import com.example.lifetrack.domain.model.PoopRecordDraft
import com.example.lifetrack.domain.model.RecordCursor
import com.example.lifetrack.domain.model.RecordDraft
import com.example.lifetrack.domain.model.StatDetail
import com.example.lifetrack.domain.model.TrackType
import com.example.lifetrack.domain.repository.RecordRepository
import com.example.lifetrack.domain.repository.StatisticsRepository
import com.example.lifetrack.domain.repository.TypeCatalogRepository
import com.example.lifetrack.feature.home.HistoryViewModel
import com.example.lifetrack.feature.home.StatisticsViewModel
import com.example.lifetrack.feature.home.TodayScreen
import com.example.lifetrack.feature.home.TodayViewModel
import com.example.lifetrack.feature.history.HistoryScreen
import com.example.lifetrack.feature.menstruation.PeriodViewModel
import com.example.lifetrack.feature.statistics.StatisticsScreen
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreensComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.now()

    @Test
    fun todayShowsModuleAndRecordCard() {
        val repository = ComposeFakeRecordRepository().apply {
            recordsForDate.value = listOf(waterRecord())
        }
        val typeRepository = ComposeFakeTypeCatalogRepository()
        val todayViewModel = TodayViewModel(repository, typeRepository, TodaySource())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val periodViewModel = PeriodViewModel(
            repository,
            typeRepository,
            SettingsRepository(context),
            TodaySource()
        )

        composeRule.setContent {
            LifeTrackTheme(darkTheme = false) {
                TodayScreen(
                    viewModel = todayViewModel,
                    periodViewModel = periodViewModel,
                    onQuickRecord = {},
                    onWaterQuick = {},
                    onDetailedRecord = {},
                    onWaterRecord = {},
                    onCoffeeRecord = {},
                    onTeaRecord = {},
                    onSportRecord = {},
                    onOpenSport = {},
                    onOpenPeriod = {},
                    onOpenSettings = {},
                    onShowDetail = {},
                    onEdit = {},
                    onDelete = {},
                    onEndPoop = {}
                )
            }
        }

        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onAllNodesWithText("饮水").onFirst().assertIsDisplayed()
    }

    @Test
    fun historyShowsRecordsAndEmptyStateCanBeReached() {
        val repository = ComposeFakeRecordRepository().apply {
            pageFactory = { HistoryPage(listOf(waterRecord()), null, hasMore = false) }
        }
        val viewModel = HistoryViewModel(
            repository,
            ComposeFakeStatisticsRepository(),
            ComposeFakeTypeCatalogRepository(),
            TodaySource()
        )

        composeRule.setContent {
            LifeTrackTheme(darkTheme = false) {
                HistoryScreen(
                    viewModel = viewModel,
                    onShowDetail = {},
                    onEdit = {},
                    onDelete = {}
                )
            }
        }

        composeRule.onNodeWithText("历史").assertIsDisplayed()
        composeRule.onNodeWithText("饮水").assertIsDisplayed()
        composeRule.onNodeWithText("已显示 1 条", substring = true).assertIsDisplayed()
    }

    @Test
    fun statisticsShowsFilterHub() {
        val statistics = ComposeFakeStatisticsRepository().apply {
            dailyCounts.value = listOf(DailyCount(today.toString(), 1))
        }
        val viewModel = StatisticsViewModel(
            statistics,
            ComposeFakeTypeCatalogRepository(),
            TodaySource()
        )

        composeRule.setContent {
            LifeTrackTheme(darkTheme = false) {
                StatisticsScreen(viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText("全部").assertIsDisplayed()
    }

    private fun waterRecord(): BackupRecord {
        val now = System.currentTimeMillis()
        return BackupRecord(
            event = TrackEventEntity(
                id = "water-record",
                typeCode = WaterFields.TYPE_CODE,
                occurredAtEpochMillis = now,
                localDate = today.toString(),
                zoneId = ZoneId.systemDefault().id,
                note = "",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            ),
            detail = RecordDetail.Generic(
                typeCode = WaterFields.TYPE_CODE,
                fields = mapOf(
                    WaterFields.AMOUNT to "250",
                    WaterFields.UNIT to "ml"
                )
            )
        )
    }
}

private class ComposeFakeRecordRepository : RecordRepository {
    val recordsForDate = MutableStateFlow<List<BackupRecord>>(emptyList())
    val recordsInRange = MutableStateFlow<List<BackupRecord>>(emptyList())
    var pageFactory: (RecordCursor?) -> HistoryPage = { HistoryPage(emptyList(), null, false) }

    override fun observeRecordsForDate(localDate: String): Flow<List<BackupRecord>> = recordsForDate

    override fun observeRecordsInRange(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<BackupRecord>> = recordsInRange

    override fun observeHistoryPage(
        startDate: String,
        endDate: String,
        typeCode: String?,
        cursor: RecordCursor?,
        limit: Int
    ): Flow<HistoryPage> = flowOf(pageFactory(cursor))

    override suspend fun addPoopRecord(draft: PoopRecordDraft): String = "poop"

    override suspend fun updatePoopRecord(eventId: String, draft: PoopRecordDraft) = Unit

    override suspend fun deletePoopRecord(eventId: String) = Unit

    override suspend fun addRecord(draft: RecordDraft): String = "record"

    override suspend fun updateRecord(eventId: String, draft: RecordDraft) = Unit
}

private class ComposeFakeStatisticsRepository : StatisticsRepository {
    val dailyCounts = MutableStateFlow<List<DailyCount>>(emptyList())
    val dailyTypeCounts = MutableStateFlow<List<DailyTypeCount>>(emptyList())
    val timestamps = MutableStateFlow<List<EventTimestamp>>(emptyList())
    val details = MutableStateFlow<List<StatDetail>>(emptyList())

    override fun observeDailyCounts(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<DailyCount>> = dailyCounts

    override fun observeDailyTypeCounts(
        startDate: String,
        endDate: String
    ): Flow<List<DailyTypeCount>> = dailyTypeCounts

    override fun observeEventTimestamps(
        startDate: String,
        endDate: String,
        typeCode: String?
    ): Flow<List<EventTimestamp>> = timestamps

    override fun observeStatDetails(
        startDate: String,
        endDate: String
    ): Flow<List<StatDetail>> = details
}

private class ComposeFakeTypeCatalogRepository : TypeCatalogRepository {
    override fun observeTrackTypes(): Flow<List<TrackType>> = flowOf(
        listOf(
            TrackType(WaterFields.TYPE_CODE, "饮水", "water", "drink", "EVENT", true, 20, 1)
        )
    )

    override suspend fun ensureDefaultTypes() = Unit
    override suspend fun migrateLegacyTeaRecords(): Int = 0
    override suspend fun setTypeEnabled(code: String, enabled: Boolean) = Unit
    override suspend fun reorderTypes(types: List<TrackType>) = Unit
}
